package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.query.GetMyScheduleUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetMyScheduleQuery;
import com.orbit.schedule.application.port.in.query.dto.MyScheduleInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkQueryPort;
import com.orbit.schedule.domain.TechnicianActor;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 기사 본인 일정(확정 일정) 조회. 구간과 일정이 겹치는, 지금 본인이 담당인 작업(작업전·작업중·완료)을 시작시각 순으로 돌려준다. 응답 전(배정됨) 작업은
 * 날짜와 관계없는 응답 필요 목록({@link GetPendingWorksService})에 있으므로 담지 않는다. 다른 기사로 바뀌었거나 대기함으로
 * 돌아간 작업은 담지 않는다. 각 작업에는 수락·거절·시작·완료보고에 쓸 지금 배정의 순번과 지연을 담는다. 관리자는 요청할 수 없다(403 SCHEDULE-005). 오류 확인
 * 순서는 요청자 → 구간 입력이다.
 */
@Service
public class GetMyScheduleService implements GetMyScheduleUseCase {

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
                .filter(work -> work.status() != WorkStatus.PENDING_ACCEPT)
                .map(work -> TechnicianSchedules.scheduledWork(work, now))
                .sorted(TechnicianSchedules.ORDER)
                .toList());
    }
}
