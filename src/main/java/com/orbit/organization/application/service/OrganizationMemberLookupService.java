package com.orbit.organization.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.OrganizationMember;
import com.orbit.organization.OrganizationMemberLookup;
import com.orbit.organization.StaffMember;
import com.orbit.organization.TechnicianMember;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.TechnicianRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.Technician;

/** 활성 소속·계약은 두 저장소에 나뉘어 있어, 한 계정에 둘 다 활성이 아니어야 한다는 규칙을 여기서 확인한다. */
@Service
public class OrganizationMemberLookupService implements OrganizationMemberLookup {

    private final MembershipRepository membershipRepository;
    private final TechnicianRepository technicianRepository;

    public OrganizationMemberLookupService(
            MembershipRepository membershipRepository, TechnicianRepository technicianRepository) {
        this.membershipRepository = membershipRepository;
        this.technicianRepository = technicianRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrganizationMember> findActiveMember(Long accountId, Long organizationId) {
        OrganizationId organization = new OrganizationId(organizationId);
        AccountId account = new AccountId(accountId);
        Optional<Membership> membership = membershipRepository.findActive(organization, account);
        Optional<Technician> technician = technicianRepository.findActive(organization, account);
        if (membership.isPresent() && technician.isPresent()) {
            throw new IllegalStateException("account must not have both active membership and technician contract");
        }
        return membership
                .<OrganizationMember>map(OrganizationMemberLookupService::toStaffMember)
                .or(() -> technician.map(OrganizationMemberLookupService::toTechnicianMember));
    }

    private static StaffMember toStaffMember(Membership membership) {
        return new StaffMember(
                membership.id().orElseThrow().value(),
                membership.organizationId().value(),
                membership.isOwner());
    }

    private static TechnicianMember toTechnicianMember(Technician technician) {
        return new TechnicianMember(
                technician.id().value(), technician.organizationId().value());
    }
}
