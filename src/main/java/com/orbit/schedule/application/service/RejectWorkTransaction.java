package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.dto.RejectWorkCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.TechnicianActor;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;

/**
 * 배정 거절의 트랜잭션 부분({@link RejectWorkService}가 저장 자리의 버전 충돌이면 새 트랜잭션에서 한 번 다시 부른다). 규칙은 {@link RejectWorkService}가
 * 설명한다. 충돌은 {@link com.orbit.schedule.application.port.out.ConcurrentWorkChangeException} 그대로 던져 이 트랜잭션을 롤백한다.
 */
@Component
public class RejectWorkTransaction {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final LockTechnicianSchedulePort lockTechnicianSchedulePort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RejectWorkTransaction(
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void reject(RejectWorkCommand command) {
        TechnicianActor technician =
                OrganizationActors.requireTechnician(loadActorPort, command.accountId(), command.organizationId());
        WorkId workId = OrganizationWorks.requireId(command.workId());
        // 작업을 읽기 전에 잠가야 잠금을 기다리는 동안 커밋된 반환·같은 요청을 본다(먼저 읽으면 영속성 컨텍스트의 낡은 작업을 쓴다).
        TechnicianScheduleLocks.lock(
                lockTechnicianSchedulePort, technician.organizationId(), technician.technicianId());
        Work work = OrganizationWorks.require(workRepository, technician.organizationId(), workId);
        AssignedTechnicians.requireVisible(work, technician);
        int assignmentNumber = AssignedTechnicians.requireAssignmentNumber(command.assignmentNumber());
        Rejection rejection = DomainRuleViolations.call(() -> new Rejection(command.reason(), command.note()));
        AssignedTechnicians.requireCurrentAssignmentOf(work, technician, assignmentNumber);

        Instant now = clock.instant();
        DomainRuleViolations.run(() -> work.reject(rejection, now));

        workRepository.save(work);
        eventPublisher.publishEvent(WorkEvents.rejected(work, technician.technicianId(), rejection, now));
        eventPublisher.publishEvent(WorkEvents.changed(work, now));
    }
}
