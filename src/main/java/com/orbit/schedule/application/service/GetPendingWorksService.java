package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.query.GetPendingWorksUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetTechnicianWorksQuery;
import com.orbit.schedule.application.port.in.query.dto.MyScheduleInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkQueryPort;
import com.orbit.schedule.domain.TechnicianActor;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 기사 응답 필요 작업 조회. 날짜와 관계없이 지금 본인에게 배정돼 수락·거절을 기다리는 작업(배정됨)을 시작시각 순으로 모두 돌려준다. 건수는 목록 길이다. 관리자는
 * 요청할 수 없다(403 SCHEDULE-005).
 */
@Service
public class GetPendingWorksService implements GetPendingWorksUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkQueryPort workQueryPort;
    private final Clock clock;

    public GetPendingWorksService(LoadActorPort loadActorPort, WorkQueryPort workQueryPort, Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workQueryPort = workQueryPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public MyScheduleInfo get(GetTechnicianWorksQuery query) {
        TechnicianActor technician =
                OrganizationActors.requireTechnician(loadActorPort, query.accountId(), query.organizationId());
        Instant now = clock.instant();

        return new MyScheduleInfo(workQueryPort
                .listCurrentForTechnician(
                        technician.organizationId(), technician.technicianId(), Set.of(WorkStatus.PENDING_ACCEPT))
                .stream()
                .map(work -> TechnicianSchedules.scheduledWork(work, now))
                .sorted(TechnicianSchedules.ORDER)
                .toList());
    }
}
