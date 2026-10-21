package com.orbit.organization.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.OrganizationAccountLookup;
import com.orbit.organization.application.port.out.MemberAccountQueryPort;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.TechnicianId;

@Service
public class OrganizationAccountLookupService implements OrganizationAccountLookup {

    private final MemberAccountQueryPort queryPort;

    public OrganizationAccountLookupService(MemberAccountQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findActiveStaffAccountId(Long organizationId, Long membershipId) {
        return queryPort
                .findActiveStaffAccountId(new OrganizationId(organizationId), new MembershipId(membershipId))
                .map(AccountId::value);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findActiveTechnicianAccountId(Long organizationId, Long technicianId) {
        return queryPort
                .findActiveTechnicianAccountId(new OrganizationId(organizationId), new TechnicianId(technicianId))
                .map(AccountId::value);
    }
}
