package com.orbit.organization.application.port.out;

import java.util.Optional;

import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;

public interface OrganizationRepository {

    Organization save(Organization organization);

    Optional<Organization> findById(OrganizationId id);

    /** 어느 발주사가 현재 회사 코드로 쓰고 있는지 확인한다. */
    boolean existsByCode(CompanyCode code);

    /**
     * 발주사 행을 트랜잭션 끝까지 잠가, 같은 발주사의 유형 생성·수정·삭제를 한 줄로 세운다. 이름 중복 확인과 저장 사이에 다른 요청이 같은 이름을 만들거나,
     * 삭제와 수정이 엇갈려 삭제된 유형이 되살아나는 경합을 막는다. 발주사가 없으면 아무것도 잠그지 않는다.
     */
    void lock(OrganizationId id);
}
