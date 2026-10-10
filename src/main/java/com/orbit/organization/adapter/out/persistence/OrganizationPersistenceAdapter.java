package com.orbit.organization.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.RetiredCompanyCode;

@Repository
class OrganizationPersistenceAdapter implements OrganizationRepository {

    private final SpringDataOrganizationRepository repository;
    private final SpringDataRetiredCompanyCodeRepository retiredCodeRepository;

    OrganizationPersistenceAdapter(
            SpringDataOrganizationRepository repository, SpringDataRetiredCompanyCodeRepository retiredCodeRepository) {
        this.repository = repository;
        this.retiredCodeRepository = retiredCodeRepository;
    }

    @Override
    public Organization save(Organization organization) {
        if (organization.id().isEmpty()) {
            return repository.save(OrganizationJpaEntity.from(organization)).toDomain();
        }
        OrganizationJpaEntity entity = repository
                .findById(organization.id().get().value())
                .orElseThrow(() -> new IllegalStateException("organization to update must exist"));
        entity.update(organization);
        return entity.toDomain();
    }

    @Override
    public Optional<Organization> findById(OrganizationId id) {
        return repository.findById(id.value()).map(OrganizationJpaEntity::toDomain);
    }

    @Override
    public Optional<Organization> findByIdForUpdate(OrganizationId id) {
        return repository.findByIdForUpdate(id.value()).map(OrganizationJpaEntity::toDomain);
    }

    @Override
    public boolean existsIssuedCode(CompanyCode code) {
        return repository.existsIssuedCode(code.value());
    }

    @Override
    public void saveRetiredCode(RetiredCompanyCode retiredCode) {
        retiredCodeRepository.save(RetiredCompanyCodeJpaEntity.from(retiredCode));
    }
}
