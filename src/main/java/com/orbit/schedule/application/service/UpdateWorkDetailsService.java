package com.orbit.schedule.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.UpdateWorkDetailsUseCase;
import com.orbit.schedule.application.port.in.command.dto.UpdateWorkDetailsCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * 작업 기본정보 수정. 작업명·작업 유형·고객정보·결제정보를 한꺼번에 교체하고 상태·배정은 그대로 둔다. 오류 확인 순서는 schedule 지침의 공통 순서를
 * 따른다. 작업 유형이 같은 조직의 것인지는 organization 계약이 연결될 때 검증한다.
 */
@Service
public class UpdateWorkDetailsService implements UpdateWorkDetailsUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;

    public UpdateWorkDetailsService(LoadActorPort loadActorPort, WorkRepository workRepository) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
    }

    @Override
    @Transactional
    public void update(UpdateWorkDetailsCommand command) {
        Actor actor = ManagingActors.require(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, actor.organizationId(), command.workId());

        DomainRuleViolations.run(() -> work.changeDetails(
                command.name(),
                command.workTypeId() == null ? null : new WorkTypeId(command.workTypeId()),
                new CustomerInfo(command.customerName(), command.customerPhone(), command.customerAddress()),
                new PaymentInfo(command.fee() == null ? null : new Money(command.fee()), command.paymentMethod())));

        workRepository.save(work);
    }
}
