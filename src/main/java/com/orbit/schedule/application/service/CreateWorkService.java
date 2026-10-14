package com.orbit.schedule.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.CreateWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.CreateWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.CreatedWorkInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.application.port.out.WorkTypePort;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * 작업 등록. 요청한 조직의 총관리자·직원이 대기함 작업을 만든다. 작업명만 필수이고 작업 유형·고객·결제 정보는 비워도 된다. 오류 확인 순서는
 * schedule 지침의 공통 순서를 따른다. 작업 유형을 지정하면 같은 조직에서 선택할 수 있는 유형이어야 한다.
 */
@Service
public class CreateWorkService implements CreateWorkUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final WorkTypePort workTypePort;

    public CreateWorkService(LoadActorPort loadActorPort, WorkRepository workRepository, WorkTypePort workTypePort) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.workTypePort = workTypePort;
    }

    @Override
    @Transactional
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

        OrganizationReferences.requireSelectableWorkType(workTypePort, work);

        Work saved = workRepository.save(work);
        return new CreatedWorkInfo(saved.id()
                .orElseThrow(() -> new IllegalStateException("saved work must have an id"))
                .value());
    }
}
