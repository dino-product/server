package com.orbit.organization.application.port.out;

import java.util.Optional;

import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.RetiredCompanyCode;

public interface OrganizationRepository {

    Organization save(Organization organization);

    Optional<Organization> findById(OrganizationId id);

    /** 같은 발주사를 바꾸는 다른 트랜잭션이 끝날 때까지 기다리도록 발주사 행을 잠그고 읽는다. */
    Optional<Organization> findByIdForUpdate(OrganizationId id);

    /** 어느 발주사의 현재 코드나 폐기 코드로 발급된 적 있는지 한 번에 확인한다. */
    boolean existsIssuedCode(CompanyCode code);

    void saveRetiredCode(RetiredCompanyCode retiredCode);
}
