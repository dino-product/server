package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.query.GetMyScheduleUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetMyScheduleQuery;
import com.orbit.schedule.application.port.in.query.dto.MyScheduleInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkQueryPort;
import com.orbit.schedule.domain.TechnicianActor;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;

/**
 * 기사 본인 일정 조회. 구간과 일정이 겹치는, 지금 본인이 담당인 작업(수락대기·수락됨·작업중·완료)을 시작시각 순으로 돌려준다. 다른 기사로 바뀌었거나 대기함으로
 * 돌아간 작업은 담지 않는다. 각 작업에는 수락·거절·시작·완료보고에 쓸 지금 배정의 순번과 지연을 담는다. 관리자는 요청할 수 없다(403 SCHEDULE-005). 오류 확인
 * 순서는 요청자 → 구간 입력이다.
 */
@Service
public class GetMyScheduleService implements GetMyScheduleUseCase {

    private static final Comparator<MyScheduleInfo.ScheduledWork> ORDER = Comparator.comparing(
                    MyScheduleInfo.ScheduledWork::startTime)
            .thenComparing(MyScheduleInfo.ScheduledWork::workId);

    private final LoadActorPort loadActorPort;
    private final WorkQueryPort workQueryPort;
    private final Clock clock;

    public GetMyScheduleService(LoadActorPort loadActorPort, WorkQueryPort workQueryPort, Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workQueryPort = workQueryPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public MyScheduleInfo get(GetMyScheduleQuery query) {
        TechnicianActor technician =
                OrganizationActors.requireTechnician(loadActorPort, query.accountId(), query.organizationId());
        QueryPeriod period = QueryPeriod.of(query.from(), query.to());
        Instant now = clock.instant();

        return new MyScheduleInfo(workQueryPort
                .listScheduledForTechnician(
                        technician.organizationId(), technician.technicianId(), period.from(), period.to())
                .stream()
                .map(work -> scheduledWork(work, now))
                .sorted(ORDER)
                .toList());
    }

    private static MyScheduleInfo.ScheduledWork scheduledWork(Work work, Instant now) {
        WorkSchedule schedule = work.schedule().orElseThrow();
        return new MyScheduleInfo.ScheduledWork(
                work.id().orElseThrow().value(),
                work.name(),
                work.status(),
                work.currentAssignmentNumber(),
                schedule.startTime(),
                schedule.endTime(),
                work.customerInfo().address().orElse(null),
                work.isDelayedAt(now));
    }
}
