package com.orbit.organization.application.port.out;

import java.util.Optional;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.TechnicianId;

/** 활성 직원 소속·기사 계약의 계정 조회. 구현은 영속성 컨텍스트에 남은 엔티티가 아니라 DB에 커밋된 상태를 읽어야 한다. */
public interface MemberAccountQueryPort {

    /** 이 직원 소속이 이 발주사의 활성 소속이면 그 계정을 읽는다. */
    Optional<AccountId> findActiveStaffAccountId(OrganizationId organizationId, MembershipId membershipId);

    /** 이 기사 계약이 이 발주사의 활성 계약이면 그 계정을 읽는다. */
    Optional<AccountId> findActiveTechnicianAccountId(OrganizationId organizationId, TechnicianId technicianId);
}
