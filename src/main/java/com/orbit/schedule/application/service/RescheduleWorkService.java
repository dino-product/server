package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.RescheduleWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.RescheduleWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;

/**
 * 같은 담당기사의 일정 변경. 시간만 바꾸며, 수락 전 작업은 바뀐 시간으로 수락을 기다리고 이미 수락한 작업은 다시 수락받지 않고 작업전을 유지한다. 반영하면
 * 수락 전·후 모두 {@link com.orbit.schedule.WorkRescheduled}를 발행한다. 작업 자신의 기존 일정은 겹침에서 빼며, 같은 시간으로의 변경은
 * SCHEDULE_UNCHANGED(409)다. 담당기사는 작업에서 가져오므로 시간 입력을 먼저 따로 검증해 공통 오류 순서(입력 400 → 상태 409)를 지킨다.
 * 현재 담당기사는 다시 확인하지 않는다. 기사 비활성화·역할 변경은 대기함 반환({@link ReleaseTechnicianWorksService})이 같은 기사 잠금으로 배정을 풀기
 * 때문이다. 탈퇴는 탈퇴 반환({@link ReleaseWithdrawnTechnicianWorksService})이 배정됨·작업전 작업을 돌리고, 남는 작업중 작업의 일정 변경은 담당기사를
 * 다시 확인하지 않는다.
 */
@Service
public class RescheduleWorkService implements RescheduleWorkUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final LockTechnicianSchedulePort lockTechnicianSchedulePort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RescheduleWorkService(
            LoadActorPort loadActorPort,
            WorkRepository workRepository,
            LockTechnicianSchedulePort lockTechnicianSchedulePort,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.lockTechnicianSchedulePort = lockTechnicianSchedulePort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ScheduleChangeInfo reschedule(RescheduleWorkCommand command) {
        ManagerActor actor =
                OrganizationActors.requireManager(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, actor.organizationId(), command.workId());
        OrganizationActors.requireRegistrarOrOwner(actor, work);
        DomainRuleViolations.run(() -> WorkSchedule.requireValidTime(command.startTime(), command.expectedDuration()));

        Instant now = clock.instant();
        WorkEvents.AssignmentBefore before = WorkEvents.AssignmentBefore.of(work);
        DomainRuleViolations.run(
                () -> work.reschedule(command.startTime(), command.expectedDuration(), now, actor.membershipId()));

        ScheduleChangeInfo result = ScheduleChanges.saveUnlessUnconfirmedConflict(
                workRepository, lockTechnicianSchedulePort, work, command.conflictConfirmed());
        if (result.applied()) {
            WorkEvents.publishAssignmentChange(eventPublisher, before, work, now);
        }
        return result;
    }
}
