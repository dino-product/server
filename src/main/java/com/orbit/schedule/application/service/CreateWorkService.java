package com.orbit.schedule.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.CreateWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.CreateWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.CreatedWorkInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * 작업 등록. 요청한 조직의 총관리자·직원이 대기함 작업을 만든다. 작업명만 필수이고 작업 유형·고객·결제 정보는 비워도 된다. 오류 확인 순서는
 * schedule 지침의 공통 순서를 따른다. 작업 유형이 같은 조직의 것인지는 organization 계약이 연결될 때 검증한다.
 */
@Service
public class CreateWorkService implements CreateWorkUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;

    public CreateWorkService(LoadActorPort loadActorPort, WorkRepository workRepository) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
    }

    @Override
    @Transactional
    public CreatedWorkInfo create(CreateWorkCommand command) {
        Actor actor = ManagingActors.require(loadActorPort, command.accountId(), command.organizationId());

        Work work = DomainRuleViolations.call(() -> Work.register(
                actor.organizationId(),
                command.name(),
                actor.membershipId(),
                command.workTypeId() == null ? null : new WorkTypeId(command.workTypeId()),
                new CustomerInfo(command.customerName(), command.customerPhone(), command.customerAddress()),
                new PaymentInfo(command.fee() == null ? null : new Money(command.fee()), command.paymentMethod())));

        Work saved = workRepository.save(work);
        return new CreatedWorkInfo(saved.id()
                .orElseThrow(() -> new IllegalStateException("saved work must have an id"))
                .value());
    }
}
