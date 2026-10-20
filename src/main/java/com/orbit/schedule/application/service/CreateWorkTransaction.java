package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.media.MediaFiles;
import com.orbit.media.StagedFiles;
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
 * 작업 등록의 트랜잭션 부분({@link CreateWorkService}가 사진 확인을 마친 뒤 부른다). 규칙은 {@link CreateWorkService}가 설명한다. 현장 사진의 확인 결과는 다른 입력과
 * 함께 검증하고, 작업을 저장한 같은 트랜잭션에서 사진을 작업에 연결한다. 겹침을 확인하지 않아 작업을 만들지 않으면 사진도 연결하지 않는다.
 */
@Component
public class CreateWorkTransaction {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final WorkTypePort workTypePort;
    private final TechnicianContractPort technicianContractPort;
    private final LockTechnicianSchedulePort lockTechnicianSchedulePort;
    private final MediaFiles mediaFiles;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CreateWorkTransaction(
            LoadActorPort loadActorPort,
            WorkRepository workRepository,
            WorkTypePort workTypePort,
            TechnicianContractPort technicianContractPort,
            LockTechnicianSchedulePort lockTechnicianSchedulePort,
            MediaFiles mediaFiles,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.workTypePort = workTypePort;
        this.technicianContractPort = technicianContractPort;
        this.lockTechnicianSchedulePort = lockTechnicianSchedulePort;
        this.mediaFiles = mediaFiles;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** @param sitePhotos 트랜잭션 밖에서 확인한 현장 사진 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CreatedWorkInfo create(CreateWorkCommand command, StagedFiles sitePhotos) {
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
        WorkPhotos.requireAccepted(sitePhotos);

        OrganizationReferences.requireSelectableWorkType(workTypePort, work);
        work.plannedSchedule()
                .map(PlannedSchedule::technicianId)
                .ifPresent(technicianId ->
                        TechnicianScheduleLocks.lock(lockTechnicianSchedulePort, actor.organizationId(), technicianId));
        OrganizationReferences.requireActivePlannedTechnician(technicianContractPort, work);

        if (schedule == null) {
            Work saved = workRepository.save(work);
            WorkPhotos.attach(mediaFiles, sitePhotos, saved.id().orElseThrow());
            return new CreatedWorkInfo(idOf(saved), ScheduleChangeInfo.applied(List.of()));
        }
        DomainRuleViolations.run(() -> work.assign(schedule, now, actor.membershipId()));
        ScheduleChanges.Outcome outcome = ScheduleChanges.apply(
                workRepository,
                lockTechnicianSchedulePort,
                work,
                command.conflictConfirmed(),
                () -> OrganizationReferences.requireActiveTechnician(technicianContractPort, work, schedule));
        // 식별자는 저장한 뒤에 생기므로 저장소가 돌려준 작업으로 만든다.
        outcome.saved().ifPresent(saved -> {
            WorkPhotos.attach(mediaFiles, sitePhotos, saved.id().orElseThrow());
            eventPublisher.publishEvent(WorkEvents.assigned(saved, now));
        });
        return new CreatedWorkInfo(
                outcome.saved().map(CreateWorkTransaction::idOf).orElse(null), outcome.info());
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
