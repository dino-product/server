package com.orbit.organization.application.port.out;

import java.util.Optional;

import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;

public interface OrganizationRepository {
    void save(Organization organization);

    void update(Organization organization);

    Optional<Organization> findById(OrganizationId id);

    Optional<Organization> findByIdForUpdate(OrganizationId id);

    boolean existsByCode(CompanyCode code);

    void flush();
}
