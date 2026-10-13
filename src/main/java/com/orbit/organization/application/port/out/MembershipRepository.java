package com.orbit.organization.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;

public interface MembershipRepository {

    /** 식별자가 없으면 새 소속을 만들고, 있으면 그 소속의 총관리자 표시를 바꾼다. */
    Membership save(Membership membership);

    Optional<Membership> findById(MembershipId id);

    /** 계정의 그 발주사 활성 직원 소속. */
    Optional<Membership> findActive(OrganizationId organizationId, AccountId accountId);

    /** 계정의 그 발주사 활성 직원 소속이 있는지. 소속을 읽어 들이지 않으므로 같은 트랜잭션에서 뒤에 읽는 {@link #findActive}가 이 시점 값을 재사용하지 않는다. */
    boolean existsActive(OrganizationId organizationId, AccountId accountId);

    /** 그 발주사에서 이 직원 유형이 지정된 모든 직원 소속(상태 무관)을 "유형 없음"으로 바꾸고 바뀐 소속 수를 돌려준다. */
    int unassignStaffCategory(OrganizationId organizationId, MemberCategoryId categoryId, Instant unassignedAt);

    /** 그 발주사의 활성 직원 소속 중 총관리자 수. */
    long countActiveOwners(OrganizationId organizationId);
}
