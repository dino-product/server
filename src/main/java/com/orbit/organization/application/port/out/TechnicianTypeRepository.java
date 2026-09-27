package com.orbit.organization.application.port.out;

import java.util.List;
import java.util.Optional;

import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.PersonnelTypeName;
import com.orbit.organization.domain.TechnicianType;
import com.orbit.organization.domain.TechnicianTypeId;

public interface TechnicianTypeRepository {
    TechnicianTypeId nextId();

    void save(TechnicianType type);

    Optional<TechnicianType> findByIdForUpdate(TechnicianTypeId id);

    List<TechnicianType> findAllByOrganization(OrganizationId organizationId);

    boolean existsByOrganizationAndName(
            OrganizationId organizationId, PersonnelTypeName name, TechnicianTypeId excludeId);

    void delete(TechnicianTypeId id);
}
