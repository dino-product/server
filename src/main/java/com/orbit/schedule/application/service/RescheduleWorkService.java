package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.RescheduleWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.RescheduleWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;

/**
 * 같은 담당기사의 일정 변경. 시간만 바꾸고, 기사가 수락한 것은 원래 시간이므로 다시 수락받는다. 작업 자신의 기존 일정은 겹침에서 빼며, 같은 시간으로의
 * 변경은 SCHEDULE_UNCHANGED(409)다. 담당기사는 작업에서 가져오므로 시간 입력을 먼저 따로 검증해 공통 오류 순서(입력 400 → 상태 409)를 지킨다.
 * 현재 담당기사가 여전히 조직의 활성 기사인지 다시 확인할지는 organization 계약을 연결할 때 정한다.
 */
@Service
public class RescheduleWorkService implements RescheduleWorkUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final LockTechnicianSchedulePort lockTechnicianSchedulePort;
    private final Clock clock;

    public RescheduleWorkService(
            LoadActorPort loadActorPort,
            WorkRepository workRepository,
            LockTechnicianSchedulePort lockTechnicianSchedulePort,
            Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.lockTechnicianSchedulePort = lockTechnicianSchedulePort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ScheduleChangeInfo reschedule(RescheduleWorkCommand command) {
        Actor actor = ManagingActors.require(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, actor.organizationId(), command.workId());
        DomainRuleViolations.run(() -> WorkSchedule.requireValidTime(command.startTime(), command.expectedDuration()));

        Instant now = clock.instant();
        DomainRuleViolations.run(() -> work.reschedule(command.startTime(), command.expectedDuration(), now));

        return ScheduleChanges.saveUnlessUnconfirmedConflict(
                workRepository, lockTechnicianSchedulePort, work, command.conflictConfirmed());
    }
}
