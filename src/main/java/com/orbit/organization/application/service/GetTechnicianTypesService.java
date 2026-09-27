package com.orbit.organization.application.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.query.GetTechnicianTypesUseCase;
import com.orbit.organization.application.port.in.query.dto.GetTechnicianTypesQuery;
import com.orbit.organization.application.port.in.query.dto.TechnicianTypeInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.TechnicianTypeRepository;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.OrganizationId;

@Service
public class GetTechnicianTypesService implements GetTechnicianTypesUseCase {
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final TechnicianTypeRepository technicianTypes;

    public GetTechnicianTypesService(
            OrganizationRepository organizations,
            MembershipRepository memberships,
            TechnicianTypeRepository technicianTypes) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.technicianTypes = technicianTypes;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianTypeInfo> get(GetTechnicianTypesQuery query) {
        Objects.requireNonNull(query.accountId(), "accountId must not be null");
        var accountId = OrganizationRuleViolations.call(() -> new AuthAccountId(query.accountId()));
        var organizationId = OrganizationRuleViolations.call(() -> new OrganizationId(query.organizationId()));
        OrganizationOwners.requireOwner(organizations.findById(organizationId), memberships, accountId);
        return technicianTypes.findAllByOrganization(organizationId).stream()
                .map(TechnicianTypeInfos::from)
                .toList();
    }
}
