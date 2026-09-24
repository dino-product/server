package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.UnassignWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.UnassignWorkCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.Work;

/**
 * 배정 해제. 수락대기·수락됨 작업을 대기함으로 돌린다. 응답 대기 중이던 배정은 해제 시각으로 마감하고 수락된 이력은 그대로 둔다. 오류 확인 순서는
 * schedule 지침의 공통 순서를 따른다.
 */
@Service
public class UnassignWorkService implements UnassignWorkUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final Clock clock;

    public UnassignWorkService(LoadActorPort loadActorPort, WorkRepository workRepository, Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void unassign(UnassignWorkCommand command) {
        Actor actor = ManagingActors.require(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, actor.organizationId(), command.workId());

        Instant now = clock.instant();
        DomainRuleViolations.run(() -> work.unassign(now));

        workRepository.save(work);
    }
}
