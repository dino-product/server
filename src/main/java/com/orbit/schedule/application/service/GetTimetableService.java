package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.query.GetTimetableUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetTimetableQuery;
import com.orbit.schedule.application.port.in.query.dto.TimetableInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkQueryPort;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkScheduleConflictPolicy;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * 관리자 타임테이블 조회. 구간과 일정이 겹치는 기사별 작업(수락대기·수락됨·작업중·완료)과 대기함 작업을 함께 돌려준다. 취소된 작업은 일정을 차지하지 않으므로
 * 칸에 넣지 않는다. 지연은 지금 시각으로, 겹침은 같은 기사의 구간 안 활성 작업끼리 일정 겹침 정책으로 표시한다. 기사는 요청할 수 없다(403 SCHEDULE-005).
 * 오류 확인 순서는 요청자 → 구간 입력이다.
 */
@Service
public class GetTimetableService implements GetTimetableUseCase {

    private static final Comparator<TimetableInfo.Entry> ENTRY_ORDER = Comparator.comparing(
                    TimetableInfo.Entry::technicianId)
            .thenComparing(TimetableInfo.Entry::startTime)
            .thenComparing(TimetableInfo.Entry::workId);

    private final LoadActorPort loadActorPort;
    private final WorkQueryPort workQueryPort;
    private final Clock clock;

    public GetTimetableService(LoadActorPort loadActorPort, WorkQueryPort workQueryPort, Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workQueryPort = workQueryPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public TimetableInfo get(GetTimetableQuery query) {
        ManagerActor manager =
                OrganizationActors.requireManager(loadActorPort, query.accountId(), query.organizationId());
        QueryPeriods.require(query.from(), query.to());
        Instant now = clock.instant();

        List<Work> scheduled =
                workQueryPort.listScheduledBetween(manager.organizationId(), query.from(), query.to()).stream()
                        .filter(work -> work.status() != WorkStatus.CANCELLED)
                        .toList();
        List<Work> active =
                scheduled.stream().filter(work -> work.status().isActive()).toList();
        List<TimetableInfo.Entry> entries = scheduled.stream()
                .map(work -> entry(work, now, active))
                .sorted(ENTRY_ORDER)
                .toList();
        List<TimetableInfo.BacklogWork> backlog = workQueryPort.listBacklog(manager.organizationId()).stream()
                .sorted(Comparator.comparing(work -> work.id().orElseThrow().value()))
                .map(GetTimetableService::backlogWork)
                .toList();
        return new TimetableInfo(entries, backlog);
    }

    private static TimetableInfo.Entry entry(Work work, Instant now, List<Work> active) {
        WorkSchedule schedule = work.schedule().orElseThrow();
        boolean conflicting = work.status().isActive()
                && !WorkScheduleConflictPolicy.findConflictingWorks(schedule, work, active)
                        .isEmpty();
        return new TimetableInfo.Entry(
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
