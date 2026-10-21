package com.orbit.organization.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.TechnicianRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.Technician;
import com.orbit.organization.domain.TechnicianId;

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
    public Optional<Technician> findById(TechnicianId id) {
        return repository.findById(id.value()).map(TechnicianJpaEntity::toDomain);
    }

    @Override
    public Optional<Technician> find(OrganizationId organizationId, AccountId accountId) {
        return repository
                .findByOrganizationIdAndAccountId(organizationId.value(), accountId.value())
                .map(TechnicianJpaEntity::toDomain);
    }

    @Override
    public Technician reactivate(Technician technician, boolean unassignCategory) {
        TechnicianJpaEntity entity = repository
                .findById(technician.id().orElseThrow().value())
                .orElseThrow(() -> new IllegalStateException("technician to reactivate must exist"));
        entity.reactivate(technician, unassignCategory);
        return entity.toDomain();
    }

    @Override
    public void deactivate(Technician technician) {
        repository
                .findById(technician.id().orElseThrow().value())
                .orElseThrow(() -> new IllegalStateException("technician to deactivate must exist"))
                .deactivate(technician);
    }

    @Override
    public void changeTechnicianCategory(TechnicianId id, MemberCategoryId categoryId) {
        repository
                .findById(id.value())
                .orElseThrow(() -> new IllegalStateException("technician to change must exist"))
                .changeCategory(categoryId == null ? null : categoryId.value());
    }

    @Override
    public int unassignTechnicianCategory(
            OrganizationId organizationId, MemberCategoryId categoryId, Instant unassignedAt) {
        return repository.unassignTechnicianCategory(organizationId.value(), categoryId.value(), unassignedAt);
    }
}
