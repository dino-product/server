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
 * 배정 해제. 수락대기·수락됨 작업을 대기함으로 돌린다. 응답 전 배정은 회수(`WITHDRAWN`)로 확정하고, 수락된 배정은 결과를 둔 채 해제 시각·처리자를 종료로 남긴다. 오류 확인 순서는
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
        Actor actor = OrganizationActors.requireManager(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, actor.organizationId(), command.workId());

        Instant now = clock.instant();
        DomainRuleViolations.run(() -> work.unassign(now, actor.membershipId()));

        workRepository.save(work);
    }
}
