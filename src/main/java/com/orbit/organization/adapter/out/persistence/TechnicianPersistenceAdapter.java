package com.orbit.organization.adapter.out.persistence;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.TechnicianRepository;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.Technician;

@Repository
class TechnicianPersistenceAdapter implements TechnicianRepository {

    private final SpringDataTechnicianRepository repository;

    TechnicianPersistenceAdapter(SpringDataTechnicianRepository repository) {
        this.repository = repository;
    }

    @Override
    public Technician save(Technician technician) {
        return repository.save(TechnicianJpaEntity.from(technician)).toDomain();
    }

    @Override
    public int unassignTechnicianCategory(
            OrganizationId organizationId, MemberCategoryId categoryId, Instant unassignedAt) {
        return repository.unassignTechnicianCategory(organizationId.value(), categoryId.value(), unassignedAt);
    }
}
