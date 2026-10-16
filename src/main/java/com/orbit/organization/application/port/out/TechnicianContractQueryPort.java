package com.orbit.organization.application.port.out;

import java.util.List;

import com.orbit.organization.domain.AccountId;

/** 기사 계약 조회. 구현은 영속성 컨텍스트에 남은 엔티티가 아니라 DB에 커밋된 상태를 읽어야 한다. */
public interface TechnicianContractQueryPort {

    /** 계정이 모든 발주사와 맺은 기사 계약을 상태와 관계없이 읽는다. */
    List<AccountTechnicianContract> listByAccount(AccountId accountId);
}
