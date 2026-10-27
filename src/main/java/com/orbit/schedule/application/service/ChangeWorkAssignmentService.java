package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.ChangeWorkAssignmentUseCase;
import com.orbit.schedule.application.port.in.command.dto.ChangeWorkAssignmentCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.orbit.schedule.application.port.out.TechnicianContractPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 타임테이블에서 작업을 놓거나 옮긴 결과(담당기사·시작시각·예상소요시간)를 반영한다. 대기함 작업이면 배정, 지금 담당기사와 같은 기사면 일정 변경, 다른 기사면
 * 재배정으로 처리하고 규칙은 각 업무와 같다. 같은 기사·같은 시간이면 SCHEDULE_UNCHANGED(409)이며 같은 기사로의 재배정 오류는 생기지 않는다. 배정·재배정은
 * 기사를 잠근 뒤 담당기사가 활성 기사 계약인지 확인하고, 일정 변경은 현재 담당기사를 다시 확인하지 않는다. 오류 확인 순서는 schedule 지침의 공통 순서를
 * 따른다. 반영하면 처리한 업무에 맞는 사실 이벤트 하나(배정 {@link com.orbit.schedule.WorkAssigned}, 재배정
 * {@link com.orbit.schedule.WorkReassigned}, 일정 변경 {@link com.orbit.schedule.WorkRescheduled})와 이어서
 * {@link com.orbit.schedule.WorkChanged}를 발행한다.
 */
@Service
public class ChangeWorkAssignmentService implements ChangeWorkAssignmentUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final TechnicianContractPort technicianContractPort;
    private final LockTechnicianSchedulePort lockTechnicianSchedulePort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ChangeWorkAssignmentService(
            LoadActorPort loadActorPort,
            WorkRepository workRepository,
            TechnicianContractPort technicianContractPort,
            LockTechnicianSchedulePort lockTechnicianSchedulePort,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.technicianContractPort = technicianContractPort;
        this.lockTechnicianSchedulePort = lockTechnicianSchedulePort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ScheduleChangeInfo change(ChangeWorkAssignmentCommand command) {
        ManagerActor actor =
                OrganizationActors.requireManager(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, actor.organizationId(), command.workId());
        // 대기함 작업의 배정은 모든 직원이, 대기함이 아닌 작업의 재배정·일정 변경은 등록자 본인·총관리자만 한다.
        if (work.status() != WorkStatus.REGISTERED) {
            OrganizationActors.requireRegistrarOrOwner(actor, work);
        }
        WorkVersions.require(work, command.expectedVersion());
        WorkSchedule requested = DomainRuleViolations.call(() -> new WorkSchedule(
                new TechnicianId(command.technicianId()), command.startTime(), command.expectedDuration()));

        Instant now = clock.instant();
        WorkEvents.AssignmentBefore before = WorkEvents.AssignmentBefore.of(work);
        Optional<WorkSchedule> current = work.schedule();
        ScheduleChangeInfo result;
        if (work.status() != WorkStatus.REGISTERED
                && current.isPresent()
                && current.get().technicianId().equals(requested.technicianId())) {
            DomainRuleViolations.run(() ->
                    work.reschedule(requested.startTime(), requested.expectedDuration(), now, actor.membershipId()));
            result = ScheduleChanges.saveUnlessUnconfirmedConflict(
                    workRepository, lockTechnicianSchedulePort, work, command.conflictConfirmed());
        } else {
            if (work.status() == WorkStatus.REGISTERED) {
                DomainRuleViolations.run(() -> work.assign(requested, now, actor.membershipId()));
            } else {
                DomainRuleViolations.run(() -> work.reassign(requested, now, actor.membershipId()));
            }
            result = ScheduleChanges.saveUnlessUnconfirmedConflict(
                    workRepository,
                    lockTechnicianSchedulePort,
                    work,
                    command.conflictConfirmed(),
                    () -> OrganizationReferences.requireActiveTechnician(technicianContractPort, work, requested));
        }
        if (result.applied()) {
            WorkEvents.publishAssignmentChange(eventPublisher, before, work, now);
        }
        return result;
    }
}
