package com.orbit.schedule.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.GetWorkHistoryUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetWorkHistoryQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkHistoryInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkQueryPort;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.AssignmentEnding;
import com.orbit.schedule.domain.AssignmentHistory;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.TechnicianActor;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.shared.error.BusinessException;

/**
 * 기사 작업 이력·통계 조회. 관리자는 조직의 어느 기사든, 기사는 본인 이력만 본다. 구간과 그 기사의 배정 일정이 겹치는 작업을 지금 상태와 관계없이 담고, 작업마다 그
 * 기사의 마지막 배정(구간과 겹치는 것 중)의 순번·일정·결과·종료 방식과 그 배정이 지금 담당인지를 준다. 통계는 이력의 작업 수, 그 배정이 지금 담당인 채 완료·취소된
 * 작업 수, 거절한 작업 수다. 수는 배정 시작시각이 속한 구간에서만 센다(경계에 걸친 일정을 양쪽 구간에서 세지 않게). 오류 확인 순서는 요청자 → 기사 식별자(관리자는 필수, 형식 400) →
 * 기사가 다른 기사를 요청하면 403(SCHEDULE-005) → 구간 입력이다.
 */
@Service
public class GetWorkHistoryService implements GetWorkHistoryUseCase {

    private static final Comparator<WorkHistoryInfo.AssignedWork> RECENT_FIRST = Comparator.comparing(
                    WorkHistoryInfo.AssignedWork::startTime)
            .thenComparing(WorkHistoryInfo.AssignedWork::workId)
            .reversed();

    private final LoadActorPort loadActorPort;
    private final WorkQueryPort workQueryPort;

    public GetWorkHistoryService(LoadActorPort loadActorPort, WorkQueryPort workQueryPort) {
        this.loadActorPort = loadActorPort;
        this.workQueryPort = workQueryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkHistoryInfo get(GetWorkHistoryQuery query) {
        Actor actor = OrganizationActors.requireMember(loadActorPort, query.accountId(), query.organizationId());
        TechnicianId technicianId = targetTechnician(actor, query.technicianId());
        QueryPeriod period = QueryPeriod.of(query.from(), query.to());

        List<Work> assigned = workQueryPort.listAssignedToTechnician(
                actor.organizationId(), technicianId, period.from(), period.to());
        List<WorkHistoryInfo.AssignedWork> works = assigned.stream()
                .map(work -> assignedWork(work, technicianId, period))
                .sorted(RECENT_FIRST)
                .toList();
        WorkHistoryInfo.Statistics statistics = new WorkHistoryInfo.Statistics(
                works.size(),
                countCurrentStartedIn(works, WorkStatus.COMPLETED, period),
                (int) assigned.stream()
                        .filter(work -> assignmentsInPeriod(work, technicianId, period)
                                .mapToObj(index -> work.assignmentHistory().get(index))
                                .anyMatch(history -> history.result() == AssignmentResult.REJECTED
                                        && !history.schedule().startTime().isBefore(period.from())))
                        .count(),
                countCurrentStartedIn(works, WorkStatus.CANCELLED, period));
        return new WorkHistoryInfo(technicianId.value(), works, statistics);
    }

    /** 이력을 볼 기사. 관리자는 반드시 지정하고, 기사는 비우거나 본인만 지정한다. */
    private static TechnicianId targetTechnician(Actor actor, Long technicianIdValue) {
        return switch (actor) {
            case ManagerActor manager -> {
                if (technicianIdValue == null) {
                    throw new BusinessException(ScheduleErrorCode.INVALID_WORK_INPUT);
                }
                yield DomainRuleViolations.call(() -> new TechnicianId(technicianIdValue));
            }
            case TechnicianActor technician -> {
                if (technicianIdValue == null) {
                    yield technician.technicianId();
                }
                TechnicianId requested = DomainRuleViolations.call(() -> new TechnicianId(technicianIdValue));
                if (!requested.equals(technician.technicianId())) {
                    throw new BusinessException(ScheduleErrorCode.ACTION_NOT_ALLOWED);
                }
                yield requested;
            }
        };
    }

    private static WorkHistoryInfo.AssignedWork assignedWork(Work work, TechnicianId technicianId, QueryPeriod period) {
        int index = assignmentsInPeriod(work, technicianId, period).max().orElseThrow();
        AssignmentHistory history = work.assignmentHistory().get(index);
        // 이 행의 배정이 작업의 마지막 배정이고 작업에 그 일정이 남아 있을 때만 지금 담당이다. 구간 밖에서 일정이 바뀌었거나 다시 배정됐으면 이 행은 지난
        // 배정이고, 되돌린 취소·해제·거절로 일정이 비었으면 담당이 없다.
        boolean current =
                index == work.assignmentHistory().size() - 1 && work.schedule().isPresent();
        return new WorkHistoryInfo.AssignedWork(
                work.id().orElseThrow().value(),
                work.name(),
                work.status(),
                current,
                index + 1,
                history.schedule().startTime(),
                history.schedule().endTime(),
                history.result(),
                history.ending().map(AssignmentEnding::reason).orElse(null));
    }

    /** 그 기사의 배정 가운데 일정이 구간과 겹치는 것의 배정 이력 위치(0부터). */
    private static IntStream assignmentsInPeriod(Work work, TechnicianId technicianId, QueryPeriod period) {
        List<AssignmentHistory> histories = work.assignmentHistory();
        return IntStream.range(0, histories.size())
                .filter(index -> histories.get(index).schedule().technicianId().equals(technicianId)
                        && period.overlaps(histories.get(index).schedule()));
    }

    /** 지금 담당인 채 그 상태가 된 작업 가운데 배정 시작시각이 구간 안인 것. 경계에 걸친 일정을 양쪽 구간에서 세지 않도록 시작이 속한 구간에서만 센다. */
    private static int countCurrentStartedIn(
            List<WorkHistoryInfo.AssignedWork> works, WorkStatus status, QueryPeriod period) {
        return (int) works.stream()
                .filter(work -> work.current() && work.status() == status)
                .filter(work -> !work.startTime().isBefore(period.from()))
                .count();
    }
}
