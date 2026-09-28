package com.orbit.organization.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.CreateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.in.command.dto.CreatedOrganizationInfo;
import com.orbit.organization.application.port.out.CompanyCodeConflictException;
import com.orbit.organization.application.port.out.CompanyCodeGenerator;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationIdentityPort;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationName;
import com.orbit.shared.error.BusinessException;

@Service
public class CreateOrganizationService implements CreateOrganizationUseCase {
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final OrganizationIdentityPort identities;
    private final CompanyCodeGenerator codes;
    private final Clock clock;

    public CreateOrganizationService(
            OrganizationRepository organizations,
            MembershipRepository memberships,
            OrganizationIdentityPort identities,
            CompanyCodeGenerator codes,
            Clock clock) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.identities = identities;
        this.codes = codes;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CreatedOrganizationInfo create(CreateOrganizationCommand command) {
        Objects.requireNonNull(command.accountId(), "accountId must not be null");
        var accountId = OrganizationRuleViolations.call(() -> new AuthAccountId(command.accountId()));
        var name = OrganizationRuleViolations.call(() -> new OrganizationName(command.name()));
        var organizationId = identities.nextOrganizationId();
        var membershipId = identities.nextMembershipId();
        var code = CompanyCodes.nextAvailableCode(codes, organizations);
        Instant joinedAt = clock.instant();
        var owner = OrganizationRuleViolations.call(
                () -> Membership.create(membershipId, organizationId, accountId, null, joinedAt));
        var organization = OrganizationRuleViolations.call(
                () -> Organization.create(organizationId, name, command.industry(), code, owner));

        try {
            organizations.save(organization);
            memberships.save(owner);
            organizations.flush();
        } catch (CompanyCodeConflictException conflict) {
            throw new BusinessException(OrganizationErrorCode.COMPANY_CODE_CONFLICT, conflict);
        }
        return new CreatedOrganizationInfo(organizationId.value(), membershipId.value(), code.value());
    }
}
