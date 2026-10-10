package com.orbit.organization.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.command.CreateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.in.command.dto.CreatedOrganizationInfo;
import com.orbit.organization.application.port.out.CompanyCodeGenerator;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.OrganizationName;

/**
 * 발주사와 생성자의 최초 총관리자 직원 소속을 한 트랜잭션에서 만든다. 요청자가 다른 발주사에 소속돼 있어도 막지 않는다. 회사 코드는
 * {@link CompanyCodeIssuer}가 발급한다.
 */
@Service
public class CreateOrganizationService implements CreateOrganizationUseCase {

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
        OrganizationName name = OrganizationInputs.name(command.name());
        Industry industry = OrganizationInputs.industry(command.industry());
        Instant now = clock.instant();

        CompanyCode code = CompanyCodeIssuer.issue(organizationRepository, codeGenerator);

        Organization organization = organizationRepository.save(Organization.create(name, industry, code, now));
        OrganizationId organizationId =
                organization.id().orElseThrow(() -> new IllegalStateException("saved organization must have an id"));
        membershipRepository.save(Membership.founder(organizationId, founder, now));

        return new CreatedOrganizationInfo(
                organizationId.value(), organization.code().value());
    }
}
