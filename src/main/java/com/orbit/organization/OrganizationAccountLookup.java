package com.orbit.organization;

import java.util.Optional;

/**
 * 발주사 구성원의 계정 공개 조회. 다른 모듈이 소속·기사 계약 식별자에서 계정을 찾을 때 쓴다. 호출 트랜잭션에서 보이는 DB 상태를 매번 새로 읽는다. 인자는
 * null이 아닌 양수이며 어기면 {@link IllegalArgumentException}이다.
 */
public interface OrganizationAccountLookup {

    /** 이 발주사의 활성 직원 소속(총관리자 포함)이면 그 계정 식별자를, 다른 발주사의 소속이거나 비활성·없는 소속이면 빈 값을 돌려준다. */
    Optional<Long> findActiveStaffAccountId(Long organizationId, Long membershipId);

    /** 이 발주사의 활성 기사 계약이면 그 계정 식별자를, 다른 발주사의 계약이거나 비활성·없는 계약이면 빈 값을 돌려준다. */
    Optional<Long> findActiveTechnicianAccountId(Long organizationId, Long technicianId);
}
