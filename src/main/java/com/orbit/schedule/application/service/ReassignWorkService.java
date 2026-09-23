package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.ReassignWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.ReassignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeResult;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;

/**
 * 담당기사 재배정. 요청한 조직의 총관리자·직원만 재배정할 수 있다. 권한(403) → 작업 식별자(400) → 작업 조회(다른 조직이면 404) → 일정 입력(400) → 도메인 재배정
 * 규칙(상태 409·같은 기사·배정 시각 400) → 새 기사 일정 겹침 순으로 확인한다. 새 기사에게 다시 수락받으며, 겹치는데 확인하지 않은 요청이면 저장하지 않고 겹친
 * 작업을 돌려준다. 같은 기사를 동시에 바꾸는 요청은 기사 단위 잠금으로 한 줄로 세우고, 대기 한도를 넘기면 409다. 새 담당기사가 같은 조직의 활성 기사인지는 organization 계약이
 * 연결될 때 검증하며, 그 전에는 이 유즈케이스를 컨트롤러로 노출하지 않는다.
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
    public ScheduleChangeResult reassign(ReassignWorkCommand command) {
        Actor actor = ManagingActors.require(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, actor.organizationId(), command.workId());
        WorkSchedule schedule = DomainRuleViolations.call(() -> new WorkSchedule(
                new MembershipId(command.technicianMembershipId()), command.startTime(), command.expectedDuration()));

        Instant now = clock.instant();
        DomainRuleViolations.run(() -> work.reassign(schedule, now));

        return ScheduleChanges.saveUnlessUnconfirmedConflict(
                workRepository, lockTechnicianSchedulePort, work, command.conflictConfirmed());
    }
}
