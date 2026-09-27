package com.orbit.schedule.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.UpdateWorkDetailsUseCase;
import com.orbit.schedule.application.port.in.command.dto.UpdateWorkDetailsCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkTypeId;
import com.orbit.shared.error.BusinessException;

/**
 * 작업 기본정보 수정. 요청한 조직의 총관리자·직원만 수정할 수 있고, 다른 조직의 작업은 존재를 드러내지 않도록 찾을 수 없음으로 처리한다. 작업 유형이 같은 조직의
 * 것인지는 organization 계약이 연결될 때 검증하며, 그 전에는 이 유즈케이스를 컨트롤러로 노출하지 않는다.
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
        WorkId workId = DomainRuleViolations.call(() -> new WorkId(command.workId()));
        Work work = workRepository
                .findById(workId)
                .filter(found -> found.organizationId().equals(actor.organizationId()))
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.WORK_NOT_FOUND));

        DomainRuleViolations.run(() -> work.changeDetails(
                command.name(),
                command.workTypeId() == null ? null : new WorkTypeId(command.workTypeId()),
                new CustomerInfo(command.customerName(), command.customerPhone(), command.customerAddress()),
                new PaymentInfo(command.fee() == null ? null : new Money(command.fee()), command.paymentMethod())));

        workRepository.save(work);
    }
}
