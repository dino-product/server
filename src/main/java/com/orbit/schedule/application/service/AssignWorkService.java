package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.AssignWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.AssignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;

/**
 * 대기함 작업 배정. 담당기사·시작시각·예상소요시간을 한 번에 지정하고 기사의 수락을 기다린다. 같은 기사의 활성 작업과 겹치면 확인한 요청만 반영한다.
 * 오류 확인 순서는 schedule 지침의 공통 순서를 따른다. 담당기사가 같은 조직의 활성 기사인지는 organization 계약이 연결될 때 검증한다.
 */
@Service
public class AssignWorkService implements AssignWorkUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final LockTechnicianSchedulePort lockTechnicianSchedulePort;
    private final Clock clock;

    public AssignWorkService(
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
    public ScheduleChangeInfo assign(AssignWorkCommand command) {
        Actor actor = ManagingActors.require(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, actor.organizationId(), command.workId());
        WorkSchedule schedule = DomainRuleViolations.call(() -> new WorkSchedule(
                new MembershipId(command.technicianMembershipId()), command.startTime(), command.expectedDuration()));

        Instant now = clock.instant();
        DomainRuleViolations.run(() -> work.assign(schedule, now));

        return ScheduleChanges.saveUnlessUnconfirmedConflict(
                workRepository, lockTechnicianSchedulePort, work, command.conflictConfirmed());
    }
}
