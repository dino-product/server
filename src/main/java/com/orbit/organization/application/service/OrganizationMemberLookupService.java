package com.orbit.organization.application.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.OrganizationMember;
import com.orbit.organization.OrganizationMemberLookup;
import com.orbit.organization.StaffMember;
import com.orbit.organization.TechnicianMember;
import com.orbit.organization.application.port.out.ActiveMember;
import com.orbit.organization.application.port.out.ActiveMemberQueryPort;
import com.orbit.organization.application.port.out.ActiveMembership;
import com.orbit.organization.application.port.out.ActiveTechnicianContract;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;

/** 활성 소속·계약은 두 저장소에 나뉘어 있어, 한 계정에 둘 다 활성이 아니어야 한다는 규칙을 여기서 확인한다. */
@Service
public class OrganizationMemberLookupService implements OrganizationMemberLookup {

    private final ActiveMemberQueryPort activeMemberQueryPort;

    public OrganizationMemberLookupService(ActiveMemberQueryPort activeMemberQueryPort) {
        this.activeMemberQueryPort = activeMemberQueryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrganizationMember> findActiveMember(Long accountId, Long organizationId) {
        OrganizationId organization = new OrganizationId(organizationId);
        List<ActiveMember> members = activeMemberQueryPort.findActiveMembers(organization, new AccountId(accountId));
        if (members.size() > 1) {
            throw new IllegalStateException("account must not have both active membership and technician contract");
        }
        return members.stream().findFirst().map(member -> toOrganizationMember(member, organization));
    }

    private static OrganizationMember toOrganizationMember(ActiveMember member, OrganizationId organizationId) {
        return switch (member) {
            case ActiveMembership membership ->
                new StaffMember(membership.membershipId().value(), organizationId.value(), membership.owner());
            case ActiveTechnicianContract contract ->
                new TechnicianMember(contract.technicianId().value(), organizationId.value());
        };
    }
}
