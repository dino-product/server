package com.orbit.organization.application.port.out;

import java.util.List;

import com.orbit.organization.domain.AccountId;

/**
 * 계정의 모든 직원 소속·기사 계약을 발주사명과 함께 읽는 조회. 두 저장소를 따로 읽으면 그 사이에 커밋된 역할 변경 때문에 한 발주사의 두 행이 모두 활성이거나 모두
 * 비활성으로 보일 수 있으므로, 구현은 한 문장(한 스냅숏)으로 읽어야 한다.
 */
public interface AccountMembershipQueryPort {

    List<AccountMembership> listMemberships(AccountId accountId);
}
