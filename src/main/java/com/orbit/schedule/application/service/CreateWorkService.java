package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

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
import com.orbit.schedule.domain.PlannedSchedule;
import com.orbit.schedule.domain.SiteAddress;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * 작업 등록. 요청한 조직의 총관리자·직원이 작업을 만든다. 작업명만 필수이고 작업 유형·고객·결제 정보는 비워도 된다. 담당기사·시작시각·예상소요시간을 모두
 * 주면 등록과 함께 배정하고(배정과 같은 규칙), 모두 비우면 대기함에 둔다. 일부만 주면 입력한 값을 미리 입력한 일정으로 남겨 대기함에 두며, 그중 담당기사가
 * 있으면 같은 조직의 활성 기사 계약이어야 한다. 그 담당기사를 잠근 뒤 확인해, 같은 기사를 잠그고 미리 입력한 담당기사를 비우는 대기함 반환과 한 줄로
 * 선다([스케줄·배정] §9). 함께 배정하려던 일정이 겹치는데 확인하지 않았으면 작업을 만들지 않는다. 확인 뒤 다시 보낸 요청이 작업을 한 번만 만들게 하기 위해서다. 오류 확인 순서는 schedule
 * 지침의 공통 순서를 따른다. 작업 유형을 지정하면 같은 조직에서 선택할 수 있는 유형이어야 한다. 현장 사진은 업로드를 마친 파일의 식별자로 최대 6장까지 받으며
 * 다른 입력과 함께 검증한다.
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
        // 일정을 함께 주면 등록과 배정을 같은 시각으로 남긴다.
        Instant now = clock.instant();

        boolean complete =
                command.technicianId() != null && command.startTime() != null && command.expectedDuration() != null;
        WorkSchedule schedule = complete
                ? DomainRuleViolations.call(() -> new WorkSchedule(
                        new TechnicianId(command.technicianId()), command.startTime(), command.expectedDuration()))
                : null;
        Work work = DomainRuleViolations.call(() -> {
            Work registered = Work.register(
                    actor.organizationId(),
                    command.name(),
                    actor.membershipId(),
                    now,
                    command.workTypeId() == null ? null : new WorkTypeId(command.workTypeId()),
                    new CustomerInfo(
                            command.customerName(),
                            command.customerPhone(),
                            new SiteAddress(
                                    command.customerPostalCode(),
                                    command.customerAddress(),
                                    command.customerAddressDetail())),
                    new PaymentInfo(command.fee() == null ? null : new Money(command.fee()), command.paymentMethod()),
                    complete ? null : plannedSchedule(command));
            registered.replaceSitePhotos(command.sitePhotoIds());
            return registered;
        });

        OrganizationReferences.requireSelectableWorkType(workTypePort, work);
        work.plannedSchedule()
                .map(PlannedSchedule::technicianId)
                .ifPresent(technicianId ->
                        TechnicianScheduleLocks.lock(lockTechnicianSchedulePort, actor.organizationId(), technicianId));
        OrganizationReferences.requireActivePlannedTechnician(technicianContractPort, work);

        if (schedule == null) {
            return new CreatedWorkInfo(idOf(workRepository.save(work)), ScheduleChangeInfo.applied(List.of()));
        }
        DomainRuleViolations.run(() -> work.assign(schedule, now, actor.membershipId()));
        ScheduleChanges.Outcome outcome = ScheduleChanges.apply(
                workRepository,
                lockTechnicianSchedulePort,
                work,
                command.conflictConfirmed(),
                () -> OrganizationReferences.requireActiveTechnician(technicianContractPort, work, schedule));
        return new CreatedWorkInfo(outcome.saved().map(CreateWorkService::idOf).orElse(null), outcome.info());
    }

    /** 일정 항목을 일부만 주었으면 미리 입력한 일정, 모두 비웠으면 null. 모두 준 경우는 호출하지 않는다. */
    private static PlannedSchedule plannedSchedule(CreateWorkCommand command) {
        if (command.technicianId() == null && command.startTime() == null && command.expectedDuration() == null) {
            return null;
        }
        return new PlannedSchedule(
                command.technicianId() == null ? null : new TechnicianId(command.technicianId()),
                command.startTime(),
                command.expectedDuration());
    }

    private static Long idOf(Work saved) {
        return saved.id()
                .orElseThrow(() -> new IllegalStateException("saved work must have an id"))
                .value();
    }
}
