package com.orbit.organization.application.port.out;

import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;

public interface OrganizationRepository {

    Organization save(Organization organization);

    /** 어느 발주사가 현재 회사 코드로 쓰고 있는지 확인한다. */
    boolean existsByCode(CompanyCode code);

    /**
     * 발주사 행을 트랜잭션 끝까지 잠가, 같은 발주사의 총관리자 지정·해제를 한 줄로 세운다. 요청자 확인보다 먼저 잡아야 서로를 동시에 해제해 총관리자가 0명이 되는
     * 경합을 막는다. 발주사가 없으면 아무것도 잠그지 않는다(요청자 소속도 없으므로 뒤 확인에서 거부된다).
     */
    void lock(OrganizationId id);
}
