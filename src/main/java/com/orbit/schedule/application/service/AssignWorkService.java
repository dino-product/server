package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.AssignWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.AssignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeResult;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeResult.ConflictingWork;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkScheduleConflictPolicy;
import com.orbit.shared.error.BusinessException;

/**
 * 대기함 작업 배정. 요청한 조직의 총관리자·직원만 배정할 수 있다. 권한(403) → 작업 식별자(400) → 작업 조회(다른 조직이면 404) → 일정 입력(400) → 도메인 배정
 * 규칙(상태 409·배정 시각 400) → 일정 겹침 순으로 확인한다. 겹치는데 확인하지 않은 요청이면 저장하지 않고 겹친 작업을 돌려준다(저장하지 않은 변경은 반영되지
 * 않는다는 {@link WorkRepository} 계약에 기댄다). 서로 다른 작업을 같은 기사에게 동시에 배정하는 경합은 아직 막지 않는다. 담당기사가 같은 조직의 활성 기사인지는
 * organization 계약이 연결될 때 검증하며, 그 전에는 이 유즈케이스를 컨트롤러로 노출하지 않는다.
 */
@Service
public class AssignWorkService implements AssignWorkUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final Clock clock;

    public AssignWorkService(LoadActorPort loadActorPort, WorkRepository workRepository, Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ScheduleChangeResult assign(AssignWorkCommand command) {
        Actor actor = ManagingActors.require(loadActorPort, command.accountId(), command.organizationId());
        WorkId workId = DomainRuleViolations.call(() -> new WorkId(command.workId()));
        Work work = workRepository
                .findById(workId)
                .filter(found -> found.organizationId().equals(actor.organizationId()))
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.WORK_NOT_FOUND));
        WorkSchedule schedule = DomainRuleViolations.call(() -> new WorkSchedule(
                new MembershipId(command.technicianMembershipId()), command.startTime(), command.expectedDuration()));

        Instant now = clock.instant();
        DomainRuleViolations.run(() -> work.assign(schedule, now));

        List<Work> candidates = workRepository.findActiveByTechnician(actor.organizationId(), schedule.technicianId());
        List<ConflictingWork> conflicts =
                WorkScheduleConflictPolicy.findConflictingWorks(schedule, work, candidates).stream()
                        .map(AssignWorkService::toConflictingWork)
                        .sorted(Comparator.comparing(ConflictingWork::startTime).thenComparing(ConflictingWork::workId))
                        .toList();
        if (!conflicts.isEmpty() && !command.conflictConfirmed()) {
            return ScheduleChangeResult.withheld(conflicts);
        }

        workRepository.save(work);
        return ScheduleChangeResult.applied(conflicts);
    }

    private static ConflictingWork toConflictingWork(Work work) {
        WorkSchedule schedule =
                work.schedule().orElseThrow(() -> new IllegalStateException("conflicting work must have a schedule"));
        Long id = work.id()
                .orElseThrow(() -> new IllegalStateException("stored work must have an id"))
                .value();
        return new ConflictingWork(id, work.name(), schedule.startTime(), schedule.endTime());
    }
}
