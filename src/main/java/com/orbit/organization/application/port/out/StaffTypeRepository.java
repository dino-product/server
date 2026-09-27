package com.orbit.organization.application.port.out;

import java.util.List;
import java.util.Optional;

import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.PersonnelTypeName;
import com.orbit.organization.domain.StaffType;
import com.orbit.organization.domain.StaffTypeId;

public interface StaffTypeRepository {
    StaffTypeId nextId();

    void save(StaffType type);

    Optional<StaffType> findByIdForUpdate(StaffTypeId id);

    List<StaffType> findAllByOrganization(OrganizationId organizationId);

    boolean existsByOrganizationAndName(OrganizationId organizationId, PersonnelTypeName name, StaffTypeId excludeId);

    void delete(StaffTypeId id);
}
