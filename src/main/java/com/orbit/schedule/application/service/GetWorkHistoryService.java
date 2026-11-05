package com.orbit.schedule.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.GetWorkHistoryUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetWorkHistoryQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkHistoryInfo;
import com.orbit.schedule.application.port.in.query.dto.WorkHistoryStatus;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LoadWorkTypeLabelsPort;
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
import com.orbit.schedule.domain.WorkTypeId;
import com.orbit.shared.error.BusinessException;

/**
 * 기사 작업 이력·통계 조회. 관리자는 조직의 어느 기사든, 기사는 본인 이력만 본다. 관리자에게는 구간과 그 기사의 배정 일정이 겹치는 작업을 지금 상태와 관계없이 담고,
 * 작업마다 그 기사의 마지막 배정(구간과 겹치는 것 중)의 순번·일정·결과·종료 방식과 그 배정이 지금 담당인지를 준다. 통계는 이력의 작업 수, 그 배정이 지금 담당인 채
 * 완료·취소된 작업 수, 거절한 작업 수다. 수는 배정 시작시각이 속한 구간에서만 센다(경계에 걸친 일정을 양쪽 구간에서 세지 않게). 기사 본인이 요청하면 그 가운데
 * 본인이 완료한 작업(구간 안 마지막 배정이 지금 담당인 채 완료된 작업)만 담고 통계도 그 작업으로만 세므로 거절·취소 수는 0이다. 거절했거나 다른 기사로 넘어간
 * 작업은 기사에게 보이지 않기 때문이다. 오류 확인 순서는 요청자 → 기사 식별자(관리자는 필수, 형식 400) → 기사가 다른 기사를 요청하면 403(SCHEDULE-005) →
 * 구간 입력이다. 누적 완료 수는 구간과 관계없이 그 기사가 지금 담당인 채 완료한 작업 수이며, 목록과 같은 시점을 보도록 한 스냅샷에서 읽는다.
 */
@Service
public class GetWorkHistoryService implements GetWorkHistoryUseCase {

    private static final Comparator<WorkHistoryInfo.AssignedWork> RECENT_FIRST = Comparator.comparing(
                    WorkHistoryInfo.AssignedWork::startTime)
            .thenComparing(WorkHistoryInfo.AssignedWork::workId)
            .reversed();

    private final LoadActorPort loadActorPort;
    private final WorkQueryPort workQueryPort;
    private final LoadWorkTypeLabelsPort loadWorkTypeLabelsPort;

    public GetWorkHistoryService(
            LoadActorPort loadActorPort, WorkQueryPort workQueryPort, LoadWorkTypeLabelsPort loadWorkTypeLabelsPort) {
        this.loadActorPort = loadActorPort;
        this.workQueryPort = workQueryPort;
        this.loadWorkTypeLabelsPort = loadWorkTypeLabelsPort;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public WorkHistoryInfo get(GetWorkHistoryQuery query) {
        Actor actor = OrganizationActors.requireMember(loadActorPort, query.accountId(), query.organizationId());
        TechnicianId technicianId = targetTechnician(actor, query.technicianId());
        QueryPeriod period = QueryPeriod.of(query.from(), query.to());
        Set<Long> workTypeIds = workTypeIds(query.workTypeIds());
        Set<WorkHistoryStatus> statuses = query.statuses() == null ? Set.of() : Set.copyOf(query.statuses());

        boolean ownHistory = actor instanceof TechnicianActor;
        List<Work> assigned = workQueryPort.listAssignedToTechnician(
                actor.organizationId(), technicianId, period.from(), period.to());
        DisplayInfos displayInfos = DisplayInfos.ofWorkTypes(loadWorkTypeLabelsPort, actor.organizationId(), assigned);
        List<Row> rows = assigned.stream()
                .map(work -> new Row(work, assignedWork(work, technicianId, period, displayInfos)))
                .filter(row -> !ownHistory || isCompletedByTechnician(row.info()))
                .sorted(Comparator.comparing(Row::info, RECENT_FIRST))
                .toList();
        List<WorkHistoryInfo.AssignedWork> works = rows.stream().map(Row::info).toList();
        // 통계는 조건과 관계없이 이력 전체로 센다(화면의 통계 카드는 목록 조건 위에 있다).
        WorkHistoryInfo.Statistics statistics = new WorkHistoryInfo.Statistics(
                works.size(),
                countCurrentStartedIn(works, WorkStatus.COMPLETED, period),
                ownHistory ? 0 : countRejectedIn(assigned, technicianId, period),
                countCurrentStartedIn(works, WorkStatus.CANCELED, period));
        List<WorkHistoryInfo.AssignedWork> listed = rows.stream()
                .filter(row -> hasWorkType(row.work(), workTypeIds, displayInfos))
                .filter(row -> hasStatus(row, statuses, technicianId, period))
                .map(Row::info)
                .toList();
        return new WorkHistoryInfo(
                technicianId.value(),
                listed,
                statistics,
                workQueryPort.countCompletedByTechnician(actor.organizationId(), technicianId));
    }

    /** 작업과 그 작업의 이력 행. 작업유형 조건은 작업으로 본다. */
    private record Row(Work work, WorkHistoryInfo.AssignedWork info) {}

    private static Set<Long> workTypeIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(ScheduleErrorCode.INVALID_WORK_INPUT);
        }
        return Set.copyOf(ids);
    }

    private static boolean hasWorkType(Work work, Set<Long> workTypeIds, DisplayInfos displayInfos) {
        return workTypeIds.isEmpty()
                || displayInfos
                        .shownWorkType(work)
                        .map(WorkTypeId::value)
                        .filter(workTypeIds::contains)
                        .isPresent();
    }

    /** 완료는 그 기사가 지금 담당인 채 완료, 거절은 그 기사가 구간과 겹치는 배정을 한 번이라도 거절(통계의 거절과 같은 기준). */
    private static boolean hasStatus(
            Row row, Set<WorkHistoryStatus> statuses, TechnicianId technicianId, QueryPeriod period) {
        return statuses.isEmpty()
                || (statuses.contains(WorkHistoryStatus.COMPLETED) && isCompletedByTechnician(row.info()))
                || (statuses.contains(WorkHistoryStatus.REJECTED)
                        && assignmentsInPeriod(row.work(), technicianId, period)
                                .mapToObj(
                                        index -> row.work().assignmentHistory().get(index))
                                .anyMatch(history -> history.result() == AssignmentResult.REJECTED));
    }

    /** 그 기사가 지금 담당인 채 완료한 작업인지. */
    private static boolean isCompletedByTechnician(WorkHistoryInfo.AssignedWork work) {
        return work.current() && work.status() == WorkStatus.COMPLETED;
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

    private static WorkHistoryInfo.AssignedWork assignedWork(
            Work work, TechnicianId technicianId, QueryPeriod period, DisplayInfos displayInfos) {
        int index = assignmentsInPeriod(work, technicianId, period).max().orElseThrow();
        AssignmentHistory history = work.assignmentHistory().get(index);
        // 이 행의 배정이 작업의 마지막 배정이고 작업에 그 일정이 남아 있을 때만 지금 담당이다. 구간 밖에서 일정이 바뀌었거나 다시 배정됐으면 이 행은 지난
        // 배정이고, 거절·배정 해제·기사 반환으로 대기함에 돌아가 일정이 비었으면 담당이 없다.
        boolean current =
                index == work.assignmentHistory().size() - 1 && work.schedule().isPresent();
        boolean completedHere = current && work.status() == WorkStatus.COMPLETED;
        return new WorkHistoryInfo.AssignedWork(
                work.id().orElseThrow().value(),
                work.name(),
                work.status(),
                current,
                index + 1,
                history.schedule().startTime(),
                history.schedule().endTime(),
                history.result(),
                history.ending().map(AssignmentEnding::reason).orElse(null),
                displayInfos.workType(work),
                completedHere ? work.startedAt().orElseThrow() : null,
                completedHere ? work.completedAt().orElseThrow() : null);
    }

    /** 그 기사의 배정 가운데 일정이 구간과 겹치는 것의 배정 이력 위치(0부터). */
    private static IntStream assignmentsInPeriod(Work work, TechnicianId technicianId, QueryPeriod period) {
        List<AssignmentHistory> histories = work.assignmentHistory();
        return IntStream.range(0, histories.size())
                .filter(index -> histories.get(index).schedule().technicianId().equals(technicianId)
                        && period.overlaps(histories.get(index).schedule()));
    }

    /** 그 기사가 시작시각이 구간 안인 배정을 한 번이라도 거절한 작업 수. */
    private static int countRejectedIn(List<Work> works, TechnicianId technicianId, QueryPeriod period) {
        return (int) works.stream()
                .filter(work -> assignmentsInPeriod(work, technicianId, period)
                        .mapToObj(index -> work.assignmentHistory().get(index))
                        .anyMatch(history -> history.result() == AssignmentResult.REJECTED
                                && !history.schedule().startTime().isBefore(period.from())))
                .count();
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
