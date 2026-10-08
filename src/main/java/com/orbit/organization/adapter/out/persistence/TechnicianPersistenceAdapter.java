package com.orbit.organization.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.TechnicianRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.Technician;
import com.orbit.organization.domain.TechnicianStatus;

@Repository
class TechnicianPersistenceAdapter implements TechnicianRepository {

    private final SpringDataTechnicianRepository repository;

    TechnicianPersistenceAdapter(SpringDataTechnicianRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Technician> findActive(OrganizationId organizationId, AccountId accountId) {
        return repository
                .findByOrganizationIdAndAccountIdAndStatus(
                        organizationId.value(), accountId.value(), TechnicianStatus.ACTIVE)
                .map(TechnicianJpaEntity::toDomain);
    }
}
