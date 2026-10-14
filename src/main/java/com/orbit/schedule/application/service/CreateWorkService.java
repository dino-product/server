package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.CreateWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.CreateWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.CreatedWorkInfo;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.orbit.schedule.application.port.out.TechnicianContractPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.application.port.out.WorkTypePort;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkTypeId;
import com.orbit.shared.error.BusinessException;

/**
 * 작업 등록. 요청한 조직의 총관리자·직원이 작업을 만든다. 작업명만 필수이고 작업 유형·고객·결제 정보는 비워도 된다. 담당기사·시작시각·예상소요시간을 모두
 * 주면 등록과 함께 배정하고(배정과 같은 규칙), 모두 비우면 대기함에 둔다. 일부만 주면 대기함에 그 값을 보관하는 기능이 아직 없어 입력 오류다(S-15). 함께
 * 배정하려던 일정이 겹치는데 확인하지 않았으면 작업을 만들지 않는다. 확인 뒤 다시 보낸 요청이 작업을 한 번만 만들게 하기 위해서다. 오류 확인 순서는 schedule
 * 지침의 공통 순서를 따른다. 작업 유형을 지정하면 같은 조직에서 선택할 수 있는 유형이어야 한다.
 */
@Service
public class CreateWorkService implements CreateWorkUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final WorkTypePort workTypePort;
    private final TechnicianContractPort technicianContractPort;
    private final LockTechnicianSchedulePort lockTechnicianSchedulePort;
    private final Clock clock;

    public CreateWorkService(
            LoadActorPort loadActorPort,
            WorkRepository workRepository,
            WorkTypePort workTypePort,
            TechnicianContractPort technicianContractPort,
            LockTechnicianSchedulePort lockTechnicianSchedulePort,
            Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.workTypePort = workTypePort;
        this.technicianContractPort = technicianContractPort;
        this.lockTechnicianSchedulePort = lockTechnicianSchedulePort;
        this.clock = clock;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CreatedWorkInfo create(CreateWorkCommand command) {
        ManagerActor actor =
                OrganizationActors.requireManager(loadActorPort, command.accountId(), command.organizationId());

        Work work = DomainRuleViolations.call(() -> Work.register(
                actor.organizationId(),
                command.name(),
                actor.membershipId(),
                command.workTypeId() == null ? null : new WorkTypeId(command.workTypeId()),
                new CustomerInfo(command.customerName(), command.customerPhone(), command.customerAddress()),
                new PaymentInfo(command.fee() == null ? null : new Money(command.fee()), command.paymentMethod())));

        WorkSchedule schedule = requestedSchedule(command);
        OrganizationReferences.requireSelectableWorkType(workTypePort, work);

        if (schedule == null) {
            return new CreatedWorkInfo(idOf(workRepository.save(work)), ScheduleChangeInfo.applied(List.of()));
        }
        Instant now = clock.instant();
        DomainRuleViolations.run(() -> work.assign(schedule, now, actor.membershipId()));
        ScheduleChanges.Outcome outcome = ScheduleChanges.apply(
                workRepository,
                lockTechnicianSchedulePort,
                work,
                command.conflictConfirmed(),
                () -> OrganizationReferences.requireActiveTechnician(technicianContractPort, work, schedule));
        return new CreatedWorkInfo(outcome.saved().map(CreateWorkService::idOf).orElse(null), outcome.info());
    }

    /** 일정 항목을 모두 비웠으면 null, 모두 주었으면 그 일정, 일부만 주었으면 입력 오류(S-15). */
    private static WorkSchedule requestedSchedule(CreateWorkCommand command) {
        boolean noTechnician = command.technicianId() == null;
        boolean noStart = command.startTime() == null;
        boolean noDuration = command.expectedDuration() == null;
        if (noTechnician && noStart && noDuration) {
            return null;
        }
        if (noTechnician || noStart || noDuration) {
            throw new BusinessException(ScheduleErrorCode.INVALID_WORK_INPUT);
        }
        return DomainRuleViolations.call(() -> new WorkSchedule(
                new TechnicianId(command.technicianId()), command.startTime(), command.expectedDuration()));
    }

    private static Long idOf(Work saved) {
        return saved.id()
                .orElseThrow(() -> new IllegalStateException("saved work must have an id"))
                .value();
    }
}
