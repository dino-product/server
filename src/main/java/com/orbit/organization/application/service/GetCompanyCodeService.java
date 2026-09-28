package com.orbit.organization.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.query.GetCompanyCodeUseCase;
import com.orbit.organization.application.port.in.query.dto.CompanyCodeInfo;
import com.orbit.organization.application.port.in.query.dto.GetCompanyCodeQuery;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.OrganizationId;

@Service
public class GetCompanyCodeService implements GetCompanyCodeUseCase {
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;

    public GetCompanyCodeService(OrganizationRepository organizations, MembershipRepository memberships) {
        this.organizations = organizations;
        this.memberships = memberships;
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyCodeInfo get(GetCompanyCodeQuery query) {
        Objects.requireNonNull(query.accountId(), "accountId must not be null");
        var accountId = OrganizationRuleViolations.call(() -> new AuthAccountId(query.accountId()));
        var organizationId = OrganizationRuleViolations.call(() -> new OrganizationId(query.organizationId()));
        var organization =
                OrganizationOwners.requireOwner(organizations.findById(organizationId), memberships, accountId);
        return new CompanyCodeInfo(
                organization.id().value(), organization.code().value());
    }
}
