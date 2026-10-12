package com.orbit.organization.application.port.out;

import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Organization;

public interface OrganizationRepository {

    Organization save(Organization organization);

    /** 어느 발주사가 현재 회사 코드로 쓰고 있는지 확인한다. */
    boolean existsByCode(CompanyCode code);
}
