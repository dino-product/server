package com.orbit.organization.application.port.out;

import java.util.Optional;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.OrganizationId;

public interface MembershipRepository {

    Membership save(Membership membership);

    Optional<Membership> findActive(OrganizationId organizationId, AccountId accountId);
}
