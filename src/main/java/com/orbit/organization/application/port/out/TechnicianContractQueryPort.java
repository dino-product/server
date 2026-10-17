package com.orbit.organization.application.port.out;

import java.util.List;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.TechnicianId;

/** 기사 계약 조회. 구현은 영속성 컨텍스트에 남은 엔티티가 아니라 DB에 커밋된 상태를 읽어야 한다. */
public interface TechnicianContractQueryPort {

    /** 계정이 모든 발주사와 맺은 기사 계약을 상태와 관계없이 읽는다. */
    List<AccountTechnicianContract> listByAccount(AccountId accountId);

    /** 이 기사 계약이 이 발주사의 활성 계약인지 읽는다. */
    boolean existsActive(OrganizationId organizationId, TechnicianId technicianId);
}
