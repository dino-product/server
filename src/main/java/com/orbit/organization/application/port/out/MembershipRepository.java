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

    /** 계정의 그 발주사 직원 소속(상태 무관). 계정·발주사당 1행이다. */
    Optional<Membership> find(OrganizationId organizationId, AccountId accountId);

    /**
     * {@link Membership#reactivate}으로 다시 활성이 된 소속의 상태·상태 변경 시각·총관리자 표시를 저장한다. {@code unassignCategory}이면 직원 유형도 "유형
     * 없음"으로 바꾼다. 직원 유형은 소속 도메인에 없으므로, 유형 삭제가 해제한 값을 되살리지 않도록 호출자는 발주사를 잠근 뒤 읽은 소속을 넘긴다.
     */
    Membership reactivate(Membership membership, boolean unassignCategory);

    /** {@link Membership#deactivate}로 비활성이 된 소속의 상태·상태 변경 시각·총관리자 표시를 저장한다. 직원 유형은 그대로 둔다. */
    void deactivate(Membership membership);

    /** 계정의 그 발주사 활성 직원 소속. */
    Optional<Membership> findActive(OrganizationId organizationId, AccountId accountId);

    /**
     * 계정의 그 발주사 활성 직원 소속이 있는지. 구현은 소속을 적재·캐시하지 않아, 같은 트랜잭션에서 발주사를 잠근 뒤 부르는 {@link #findActive}가 그
     * 사이 커밋된 값을 읽게 해야 한다.
     */
    boolean existsActive(OrganizationId organizationId, AccountId accountId);

    /** 그 발주사에서 이 직원 유형이 지정된 모든 직원 소속(상태 무관)을 "유형 없음"으로 바꾸고 바뀐 소속 수를 돌려준다. */
    int unassignStaffCategory(OrganizationId organizationId, MemberCategoryId categoryId, Instant unassignedAt);

    /** 직원 유형을 바꾼다. {@code categoryId}가 {@code null}이면 "유형 없음"이다. 유형은 소속 도메인에 없으므로 호출자는 발주사를 잠근 뒤 유형을 확인한다. */
    void changeStaffCategory(MembershipId id, MemberCategoryId categoryId);

    /** 그 발주사의 활성 직원 소속 중 총관리자 수. */
    long countActiveOwners(OrganizationId organizationId);
}
