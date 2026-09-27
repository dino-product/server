package com.orbit.organization.application.port.out;

import java.util.Optional;

import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.OrganizationId;

public interface MembershipRepository {
    void save(Membership membership);

    Optional<Membership> findByOrganizationAndAccount(OrganizationId organizationId, AuthAccountId accountId);
}
