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
 * 배정 해제. 요청한 조직의 총관리자·직원만 해제할 수 있다. 권한(403) → 작업 식별자(400) → 작업 조회(다른 조직이면 404) → 도메인 해제 규칙(수락대기·수락됨이 아니면
 * 409, 수락대기면 대기 중이던 배정을 해제 시각으로 마감하므로 그 시각이 배정 시각보다 이르면 400) 순으로 확인한다. 수락된 배정 이력은 그대로 둔다. 해제된 작업은 대기함으로 돌아간다.
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
