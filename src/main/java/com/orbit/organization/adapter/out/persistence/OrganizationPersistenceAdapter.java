package com.orbit.organization.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;

@Repository
class OrganizationPersistenceAdapter implements OrganizationRepository {

    private final SpringDataOrganizationRepository repository;

    OrganizationPersistenceAdapter(SpringDataOrganizationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Organization save(Organization organization) {
        return repository.save(OrganizationJpaEntity.from(organization)).toDomain();
    }

    @Override
    public Optional<Organization> findById(OrganizationId id) {
        return repository.findById(id.value()).map(OrganizationJpaEntity::toDomain);
    }

    @Override
    public boolean existsByCode(CompanyCode code) {
        return repository.existsByCode(code.value());
    }
}
