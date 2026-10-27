package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.CancelWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.CancelWorkCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.Work;

/**
 * 작업 취소. 완료 전(등록·수락대기·수락됨·작업중) 작업을 취소하고 지금 시각·처리자·사유를 작업에 남긴다. 배정이 있던 작업은 그 배정도 같은 시각·처리자의
 * 취소로 끝난다. 이미 취소됐거나 완료된 작업은 409(SCHEDULE-002)다. 취소는 기사의 활성 작업을 줄이기만 하므로 기사 잠금은 잡지 않는다. 기사의 요청과 같은
 * 순간에 들어온 취소는 작업 버전 검사(HM-236)로 뒤 요청을 실패시키며, 겹친 뒤 요청은 아직 500으로 실패한다. 오류 확인 순서는 schedule 지침의
 * 공통 순서를 따른다. 저장한 뒤 취소 당시 담당 기사(대기함이면 없음)와 함께 {@link com.orbit.schedule.WorkCancelled}를 발행한다.
 */
@Service
public class CancelWorkService implements CancelWorkUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CancelWorkService(
            LoadActorPort loadActorPort,
            WorkRepository workRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
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
