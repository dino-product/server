package com.orbit.organization.adapter.out.persistence;

import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Organization;

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
    public boolean existsByCode(CompanyCode code) {
        return repository.existsByCode(code.value());
    }
}
