package com.orbit.schedule.application.service;

import java.time.Clock;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.UpdateWorkDetailsUseCase;
import com.orbit.schedule.application.port.in.command.dto.UpdateWorkDetailsCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.application.port.out.WorkTypePort;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.SiteAddress;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * 작업 기본정보 수정. 작업명·작업 유형·고객정보·결제정보·현장 사진을 한꺼번에 교체하고 상태·배정은 그대로 둔다. 오류 확인 순서는 schedule 지침의 공통 순서를
 * 따른다. 작업 유형을 바꾸면 같은 조직에서 선택할 수 있는 유형이어야 하고, 작업중에는 바꿀 수 없다.
 */
@Service
public class UpdateWorkDetailsService implements UpdateWorkDetailsUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final WorkTypePort workTypePort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public UpdateWorkDetailsService(
            LoadActorPort loadActorPort,
            WorkRepository workRepository,
            WorkTypePort workTypePort,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.workTypePort = workTypePort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void update(UpdateWorkDetailsCommand command) {
        ManagerActor actor =
                OrganizationActors.requireManager(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, actor.organizationId(), command.workId());
        OrganizationActors.requireRegistrarOrOwner(actor, work);
        Optional<WorkTypeId> workTypeBefore = work.workType();

        DomainRuleViolations.run(() -> work.changeDetails(
                command.name(),
                command.workTypeId() == null ? null : new WorkTypeId(command.workTypeId()),
                new CustomerInfo(
                        command.customerName(),
                        command.customerPhone(),
                        new SiteAddress(
                                command.customerPostalCode(),
                                command.customerAddress(),
                                command.customerAddressDetail())),
                new PaymentInfo(command.fee() == null ? null : new Money(command.fee()), command.paymentMethod()),
                command.sitePhotoIds()));
        // 지금 유형을 그대로 보낸 수정은 다시 확인하지 않는다. 작업중 작업이 그 사이 삭제된 유형을 가져도 다른 항목을 고칠 수 있다.
        if (!work.workType().equals(workTypeBefore)) {
            OrganizationReferences.requireSelectableWorkType(workTypePort, work);
        }

        workRepository.save(work);
        eventPublisher.publishEvent(WorkEvents.changed(work, clock.instant()));
    }
}
