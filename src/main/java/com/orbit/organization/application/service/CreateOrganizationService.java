package com.orbit.organization.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.CreateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.in.command.dto.CreatedOrganizationInfo;
import com.orbit.organization.application.port.out.CompanyCodeGenerator;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.OrganizationName;
import com.orbit.shared.error.BusinessException;

/**
 * 발주사와 생성자의 최초 총관리자 직원 소속을 한 트랜잭션에서 만든다. 요청자가 다른 발주사에 소속돼 있어도 막지 않는다.
 *
 * <p>회사 코드는 현재 쓰이는 코드와 겹치지 않을 때까지 후보를 다시 만든다. 사전 확인과 저장 사이의 동시 발급은 {@code companies.code} 유일 제약이
 * 막으며, 이 경우 요청은 실패하고 클라이언트가 다시 시도한다. 32^6 후보 공간에서 겹칠 확률이 매우 낮아 재시도 횟수를 작게 둔다.
 */
@Service
public class CreateOrganizationService implements CreateOrganizationUseCase {

    static final int MAX_CODE_ATTEMPTS = 5;

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final CompanyCodeGenerator codeGenerator;
    private final Clock clock;

    public CreateOrganizationService(
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            CompanyCodeGenerator codeGenerator,
            Clock clock) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CreatedOrganizationInfo create(CreateOrganizationCommand command) {
        AccountId founder = new AccountId(command.accountId());
        OrganizationName name = validInput(() -> new OrganizationName(command.name()));
        if (command.industry() == null) {
            throw new BusinessException(OrganizationErrorCode.INVALID_ORGANIZATION_INPUT);
        }
        Instant now = clock.instant();

        Organization organization =
                organizationRepository.save(Organization.create(name, command.industry(), issueCode(), now));
        OrganizationId organizationId =
                organization.id().orElseThrow(() -> new IllegalStateException("saved organization must have an id"));
        membershipRepository.save(Membership.founder(organizationId, founder, now));

        return new CreatedOrganizationInfo(
                organizationId.value(), organization.code().value());
    }

    private CompanyCode issueCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            CompanyCode candidate = codeGenerator.generate();
            if (!organizationRepository.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("could not issue a unique company code");
    }

    private static <T> T validInput(Supplier<T> factory) {
        try {
            return factory.get();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(OrganizationErrorCode.INVALID_ORGANIZATION_INPUT, exception);
        }
    }
}
