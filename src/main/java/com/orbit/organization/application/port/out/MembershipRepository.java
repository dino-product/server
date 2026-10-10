package com.orbit.organization.application.port.out;

import java.util.Optional;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.OrganizationId;

public interface MembershipRepository {

    Membership save(Membership membership);

    /** 계정의 그 발주사 직원 소속 중 활성인 것. */
    Optional<Membership> findActive(OrganizationId organizationId, AccountId accountId);
}
