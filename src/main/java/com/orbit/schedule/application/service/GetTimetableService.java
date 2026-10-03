package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.query.GetTimetableUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetTimetableQuery;
import com.orbit.schedule.application.port.in.query.dto.TimetableInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkQueryPort;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkScheduleConflictPolicy;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * 관리자 타임테이블 조회. 구간과 일정이 겹치는 기사별 작업(수락대기·수락됨·작업중·완료)과 대기함 작업을 함께 돌려준다. 지연은 지금 시각으로, 겹침은 같은 기사의
 * 활성 작업끼리 일정 겹침 정책으로 표시한다. 겹치는 짝이 구간 밖에 있어도 표시하도록, 예상소요시간 상한만큼 넓힌 구간의 작업을 겹침 후보로 읽는다. 칸과 대기함을
 * 같은 시점의 상태로 보이도록 한 스냅샷(REPEATABLE READ)에서 읽는다. 기사는 요청할 수 없다(403 SCHEDULE-005). 오류 확인 순서는 요청자 → 구간 입력이다.
 */
@Service
public class GetTimetableService implements GetTimetableUseCase {

    private static final Comparator<TimetableInfo.ScheduledWork> SCHEDULED_ORDER = Comparator.comparing(
                    TimetableInfo.ScheduledWork::technicianId)
            .thenComparing(TimetableInfo.ScheduledWork::startTime)
            .thenComparing(TimetableInfo.ScheduledWork::workId);

    private final LoadActorPort loadActorPort;
    private final WorkQueryPort workQueryPort;
    private final Clock clock;

    public GetTimetableService(LoadActorPort loadActorPort, WorkQueryPort workQueryPort, Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workQueryPort = workQueryPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TimetableInfo get(GetTimetableQuery query) {
        ManagerActor manager =
                OrganizationActors.requireManager(loadActorPort, query.accountId(), query.organizationId());
        QueryPeriod period = QueryPeriod.of(query.from(), query.to());
        Instant now = clock.instant();

        // 구간 안 작업과 겹칠 수 있는 작업은 시작·종료가 구간에서 예상소요시간 상한 이내에 있다.
        QueryPeriod candidateWindow = period.widenedBy(WorkSchedule.MAX_EXPECTED_DURATION);
        List<Work> candidates = workQueryPort.listScheduledBetween(
                manager.organizationId(), candidateWindow.from(), candidateWindow.to());
        Map<TechnicianId, List<Work>> activeByTechnician = candidates.stream()
                .filter(work -> work.status().isActive())
                .collect(Collectors.groupingBy(
                        work -> work.schedule().orElseThrow().technicianId()));
        List<TimetableInfo.ScheduledWork> scheduled = candidates.stream()
                .filter(work -> period.overlaps(work.schedule().orElseThrow()))
                .map(work -> scheduledWork(work, now, activeByTechnician))
                .sorted(SCHEDULED_ORDER)
                .toList();
        List<TimetableInfo.BacklogWork> backlog = workQueryPort.listBacklog(manager.organizationId()).stream()
                .sorted(Comparator.comparing(work -> work.id().orElseThrow().value()))
                .map(GetTimetableService::backlogWork)
                .toList();
        return new TimetableInfo(scheduled, backlog);
    }

    private static TimetableInfo.ScheduledWork scheduledWork(
            Work work, Instant now, Map<TechnicianId, List<Work>> activeByTechnician) {
        WorkSchedule schedule = work.schedule().orElseThrow();
        boolean conflicting = work.status().isActive()
                && !WorkScheduleConflictPolicy.findConflictingWorks(
                                schedule, work, activeByTechnician.getOrDefault(schedule.technicianId(), List.of()))
                        .isEmpty();
        return new TimetableInfo.ScheduledWork(
                work.id().orElseThrow().value(),
                work.name(),
                work.status(),
                schedule.technicianId().value(),
                schedule.startTime(),
                schedule.endTime(),
                work.isDelayedAt(now),
                conflicting);
    }

    private static TimetableInfo.BacklogWork backlogWork(Work work) {
        return new TimetableInfo.BacklogWork(
                work.id().orElseThrow().value(),
                work.name(),
                work.workType().map(WorkTypeId::value).orElse(null),
                work.isReturnedByRejection());
    }
}
