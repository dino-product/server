package com.orbit.organization.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.command.UpdateOrganizationDetailsUseCase;
import com.orbit.organization.application.port.in.command.dto.UpdateOrganizationDetailsCommand;
import com.orbit.organization.application.port.in.query.dto.OrganizationDetailsInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.OrganizationName;

@Service
public class UpdateOrganizationDetailsService implements UpdateOrganizationDetailsUseCase {
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;

    public UpdateOrganizationDetailsService(OrganizationRepository organizations, MembershipRepository memberships) {
        this.organizations = organizations;
        this.memberships = memberships;
    }

    @Override
    @Transactional
    public OrganizationDetailsInfo update(UpdateOrganizationDetailsCommand command) {
        Objects.requireNonNull(command.accountId(), "accountId must not be null");
        var accountId = OrganizationRuleViolations.call(() -> new AuthAccountId(command.accountId()));
        var organizationId = OrganizationRuleViolations.call(() -> new OrganizationId(command.organizationId()));
        var organization = OrganizationOwners.requireOwner(
                organizations.findByIdForUpdate(organizationId), memberships, accountId);
        var name = OrganizationRuleViolations.call(() -> new OrganizationName(command.name()));
        OrganizationRuleViolations.call(() -> {
            organization.updateDetails(name, command.industry());
            return null;
        });
        organizations.update(organization);
        return new OrganizationDetailsInfo(
                organization.id().value(), organization.name().value(), organization.industry());
    }
}
