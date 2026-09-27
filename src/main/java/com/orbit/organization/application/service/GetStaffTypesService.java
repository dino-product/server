package com.orbit.organization.application.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.query.GetStaffTypesUseCase;
import com.orbit.organization.application.port.in.query.dto.GetStaffTypesQuery;
import com.orbit.organization.application.port.in.query.dto.StaffTypeInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.StaffTypeRepository;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.OrganizationId;

@Service
public class GetStaffTypesService implements GetStaffTypesUseCase {
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final StaffTypeRepository staffTypes;

    public GetStaffTypesService(
            OrganizationRepository organizations, MembershipRepository memberships, StaffTypeRepository staffTypes) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.staffTypes = staffTypes;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffTypeInfo> get(GetStaffTypesQuery query) {
        Objects.requireNonNull(query.accountId(), "accountId must not be null");
        var accountId = OrganizationRuleViolations.call(() -> new AuthAccountId(query.accountId()));
        var organizationId = OrganizationRuleViolations.call(() -> new OrganizationId(query.organizationId()));
        OrganizationOwners.requireOwner(organizations.findById(organizationId), memberships, accountId);
        return staffTypes.findAllByOrganization(organizationId).stream()
                .map(StaffTypeInfos::from)
                .toList();
    }
}
