package com.orbit.organization.application.port.out;

import java.util.List;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;

/**
 * 계정의 발주사 내 활성 직원 소속·기사 계약을 한 번에 읽는 조회. 두 저장소를 따로 읽으면 그 사이에 커밋된 역할 변경 때문에 둘 다 활성으로 보일 수 있으므로, 구현은
 * 한 문장(한 스냅숏)으로 읽어야 한다. 불변식이 지켜지면 결과는 0~1개다.
 */
public interface ActiveMemberQueryPort {

    List<ActiveMember> findActiveMembers(OrganizationId organizationId, AccountId accountId);
}
