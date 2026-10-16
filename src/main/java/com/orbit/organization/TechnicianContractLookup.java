package com.orbit.organization;

import java.util.List;

/**
 * 기사 계약 공개 조회. {@link OrganizationMemberLookup}은 계정이 한 발주사에서 지금 어떤 구성원인지를 보고, 이 조회는 기사 계약 자체를 본다.
 * 캐시·영속성 컨텍스트가 아닌, 호출 트랜잭션에서 보이는 DB 상태를 매번 새로 읽는다(READ COMMITTED면 호출 시점에 커밋된 상태). 인자는 null이 아닌 양수이며
 * 어기면 {@link IllegalArgumentException}이다.
 */
public interface TechnicianContractLookup {

    /**
     * 계정이 발주사들과 맺은 기사 계약을 모두 돌려준다. 발주사를 가리지 않고 비활성(역할 변경으로 끝난 행 포함) 계약도 활성 여부와 함께 담는다. 한 계정은 한
     * 발주사와 기사 계약을 하나만 맺으므로 발주사마다 많아야 한 건이다. 계약이 없으면 빈 목록이고 순서는 보장하지 않는다.
     */
    List<TechnicianContract> listByAccount(Long accountId);
}
