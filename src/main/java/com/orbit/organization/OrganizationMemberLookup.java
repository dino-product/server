package com.orbit.organization;

import java.util.Optional;

/**
 * 계정이 발주사에서 지금 어떤 구성원인지 찾는 공개 조회. 활성 직원 소속(총관리자 여부 포함) 또는 활성 기사 계약 중 하나를 돌려주고, 비활성(역할 변경으로 끝난 행 포함)
 * 소속·계약과 다른 발주사의 것은 보지 않는다. 한 계정은 한 발주사에서 둘 중 하나만 활성일 수 있으므로, 둘 다 활성이면 불변식 위반으로 {@link IllegalStateException}을
 * 던진다. 인자는 null이 아닌 양수다.
 */
public interface OrganizationMemberLookup {

    Optional<OrganizationMember> findActiveMember(Long accountId, Long organizationId);
}
