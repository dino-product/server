package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.RescheduleWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.RescheduleWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeResult;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.Work;

/**
 * 같은 담당기사의 일정 변경. 요청한 조직의 총관리자·직원만 바꿀 수 있다. 권한(403) → 작업 식별자(400) → 작업 조회(다른 조직이면 404) → 도메인 일정 변경
 * 규칙(상태 409 → 일정 입력·같은 일정·배정 시각 400) → 일정 겹침 순으로 확인한다. 배정·재배정과 달리 바꿀 수 없는 상태면 입력과 관계없이 409다(현재 기사를 쓰려면
 * 배정돼 있어야 하므로 도메인이 상태부터 본다). 기사가 수락한 것은 원래 시간이므로 다시 수락받으며, 작업 자신의 기존 일정은 겹침에서 제외한다. 겹치는데 확인하지
 * 않은 요청이면 저장하지 않고 겹친 작업을 돌려준다. 같은 기사를 동시에 바꾸는 요청은 기사 단위 잠금으로 한 줄로 세우고, 대기 한도를 넘기면 409다. 현재 담당기사가 여전히 조직의 활성
 * 기사인지 다시 확인할지는 organization 계약을 연결할 때 정한다.
 */
@Service
public class RescheduleWorkService implements RescheduleWorkUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final LockTechnicianSchedulePort lockTechnicianSchedulePort;
    private final Clock clock;

    public RescheduleWorkService(
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
    public ScheduleChangeResult reschedule(RescheduleWorkCommand command) {
        Actor actor = ManagingActors.require(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, actor.organizationId(), command.workId());

        Instant now = clock.instant();
        DomainRuleViolations.run(() -> work.reschedule(command.startTime(), command.expectedDuration(), now));

        return ScheduleChanges.saveUnlessUnconfirmedConflict(
                workRepository, lockTechnicianSchedulePort, work, command.conflictConfirmed());
    }
}
