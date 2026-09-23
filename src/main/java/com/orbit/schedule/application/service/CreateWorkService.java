package com.orbit.schedule.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.CreateWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.CreateWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.CreatedWorkInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkTypeId;
import com.orbit.shared.error.BusinessException;

/**
 * 작업 등록. 요청한 조직의 총관리자·직원만 등록할 수 있고 등록자는 요청자의 소속이다. 권한을 입력 검증보다 먼저 확인해 권한 없는 요청에 입력 오류가 드러나지 않게 한다.
 * 작업 유형이 같은 조직의 것인지는 organization 계약이 연결될 때 검증하며, 그 전에는 이 유즈케이스를 컨트롤러로 노출하지 않는다.
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
        // 인증 계층이 채우는 값이라 비어 있으면 프로그래밍 오류다.
        Objects.requireNonNull(command.accountId(), "accountId must not be null");
        OrganizationId organizationId = DomainRuleViolations.call(() -> new OrganizationId(command.organizationId()));
        Actor actor = loadActorPort
                .findActiveActor(command.accountId(), organizationId)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.NOT_ORGANIZATION_MEMBER));
        if (!actor.organizationId().equals(organizationId)) {
            throw new IllegalStateException("actor must belong to the requested organization");
        }
        if (!actor.canManageWorks()) {
            throw new BusinessException(ScheduleErrorCode.ACTION_NOT_ALLOWED);
        }

        Work work = DomainRuleViolations.call(() -> Work.register(
                organizationId,
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
