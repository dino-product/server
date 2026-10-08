package com.orbit.organization.application.port.out;

import java.util.Optional;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;

public interface MembershipRepository {

    /** 식별자가 없으면 새 소속을 만들고, 있으면 그 소속의 총관리자 표시를 바꾼다. */
    Membership save(Membership membership);

    Optional<Membership> findById(MembershipId id);

    /** 계정의 그 발주사 활성 직원 소속. */
    Optional<Membership> findActive(OrganizationId organizationId, AccountId accountId);

    /** 그 발주사의 활성 직원 소속 중 총관리자 수. */
    long countActiveOwners(OrganizationId organizationId);
}
