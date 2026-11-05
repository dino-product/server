package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.query.GetActionableWorksUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetTechnicianWorksQuery;
import com.orbit.schedule.application.port.in.query.dto.MyScheduleInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkQueryPort;
import com.orbit.schedule.domain.TechnicianActor;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 기사가 지금 수행할 수 있는 작업 조회. 조회 시각이 그 일정의 시작 가능 시간({@link WorkSchedule#isTooEarlyToStart}·{@link
 * WorkSchedule#isTooLateToStart}) 안인 작업전 작업과, 언제 시작했든 작업중인 작업을 시작시각 순으로 돌려준다. 작업 시작과 같은 판정을 써서 목록에 있는
 * 작업전 작업은 바로 시작할 수 있다. 관리자는 요청할 수 없다(403 SCHEDULE-005).
 */
@Service
public class GetActionableWorksService implements GetActionableWorksUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkQueryPort workQueryPort;
    private final Clock clock;

    public GetActionableWorksService(LoadActorPort loadActorPort, WorkQueryPort workQueryPort, Clock clock) {
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
                        technician.organizationId(),
                        technician.technicianId(),
                        Set.of(WorkStatus.ACCEPTED, WorkStatus.IN_PROGRESS))
                .stream()
                .filter(work -> isActionableAt(work, now))
                .map(work -> TechnicianSchedules.scheduledWork(work, now))
                .sorted(TechnicianSchedules.ORDER)
                .toList());
    }

    private static boolean isActionableAt(Work work, Instant now) {
        if (work.status() == WorkStatus.IN_PROGRESS) {
            return true;
        }
        WorkSchedule schedule = work.schedule().orElseThrow();
        return !schedule.isTooEarlyToStart(now) && !schedule.isTooLateToStart(now);
    }
}
