package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.ReassignWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.ReassignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;

/**
 * 담당기사 재배정. 다른 기사와 그 기사의 일정으로 바꾸고 새 기사에게 다시 수락받는다. 새 기사의 활성 작업과 겹치면 확인한 요청만 반영하며, 현재
 * 기사로의 재배정은 SAME_TECHNICIAN(409)이다. 오류 확인 순서는 schedule 지침의 공통 순서를 따른다. 새 담당기사가 같은 조직의 활성 기사인지는
 * organization 계약이 연결될 때 검증한다.
 */
@Service
public class ReassignWorkService implements ReassignWorkUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final LockTechnicianSchedulePort lockTechnicianSchedulePort;
    private final Clock clock;

    public ReassignWorkService(
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
    public ScheduleChangeInfo reassign(ReassignWorkCommand command) {
        Actor actor = OrganizationActors.requireManager(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, actor.organizationId(), command.workId());
        WorkSchedule schedule = DomainRuleViolations.call(() -> new WorkSchedule(
                new MembershipId(command.technicianMembershipId()), command.startTime(), command.expectedDuration()));

        Instant now = clock.instant();
        DomainRuleViolations.run(() -> work.reassign(schedule, now, actor.membershipId()));

        return ScheduleChanges.saveUnlessUnconfirmedConflict(
                workRepository, lockTechnicianSchedulePort, work, command.conflictConfirmed());
    }
}
