package com.orbit.organization.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.OrganizationId;

public interface MembershipRepository {

    Membership save(Membership membership);

    /** 계정의 그 발주사 직원 소속 중 활성인 것. */
    Optional<Membership> findActive(OrganizationId organizationId, AccountId accountId);

    /** 그 발주사에서 이 직원 유형이 지정된 모든 직원 소속(상태 무관)을 "유형 없음"으로 바꾸고 바뀐 소속 수를 돌려준다. */
    int unassignStaffCategory(OrganizationId organizationId, MemberCategoryId categoryId, Instant unassignedAt);
}
