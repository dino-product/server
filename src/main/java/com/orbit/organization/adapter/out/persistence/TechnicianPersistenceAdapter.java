package com.orbit.organization.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.TechnicianRepository;
import com.orbit.organization.domain.AccountId;
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
    public Technician withdraw(Technician technician) {
        TechnicianJpaEntity entity = repository
                .findById(technician.id().orElseThrow().value())
                .orElseThrow(() -> new IllegalStateException("technician to withdraw must exist"));
        entity.withdraw(technician);
        return entity.toDomain();
    }

    @Override
    public List<OrganizationId> listOrganizationsOf(AccountId accountId) {
        return repository.findOrganizationIdsByAccountId(accountId.value()).stream()
                .map(OrganizationId::new)
                .toList();
    }

    @Override
    public int unassignTechnicianCategory(
            OrganizationId organizationId, MemberCategoryId categoryId, Instant unassignedAt) {
        return repository.unassignTechnicianCategory(organizationId.value(), categoryId.value(), unassignedAt);
    }
}
