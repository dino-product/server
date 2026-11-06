package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.dto.CancelWorkCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.Work;

/**
 * 작업 취소의 트랜잭션 부분({@link CancelWorkService}가 저장 자리의 버전 충돌이면 새 트랜잭션에서 한 번 다시 부른다). 규칙은 {@link CancelWorkService}가
 * 설명한다. 충돌은 {@link com.orbit.schedule.application.port.out.ConcurrentWorkChangeException} 그대로 던져 이 트랜잭션을 롤백한다.
 */
@Component
public class CancelWorkTransaction {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CancelWorkTransaction(
            LoadActorPort loadActorPort,
            WorkRepository workRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void cancel(CancelWorkCommand command) {
        ManagerActor actor =
                OrganizationActors.requireManager(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, actor.organizationId(), command.workId());
        OrganizationActors.requireRegistrarOrOwner(actor, work);

        Instant now = clock.instant();
        WorkEvents.AssignmentBefore before = WorkEvents.AssignmentBefore.of(work);
        DomainRuleViolations.run(() -> work.cancel(now, actor.membershipId(), command.reason(), command.note()));

        workRepository.save(work);
        eventPublisher.publishEvent(WorkEvents.cancelled(before, work, now));
        eventPublisher.publishEvent(WorkEvents.changed(work, now));
    }
}
