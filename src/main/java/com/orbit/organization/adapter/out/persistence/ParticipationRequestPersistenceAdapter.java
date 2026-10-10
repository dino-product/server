package com.orbit.organization.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.ParticipationRequestRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationRequest;
import com.orbit.organization.domain.ParticipationRequestStatus;

@Repository
class ParticipationRequestPersistenceAdapter implements ParticipationRequestRepository {

    private final SpringDataParticipationRequestRepository repository;

    ParticipationRequestPersistenceAdapter(SpringDataParticipationRequestRepository repository) {
        this.repository = repository;
    }

    @Override
    public ParticipationRequest save(ParticipationRequest request) {
        if (request.id().isEmpty()) {
            return repository.save(ParticipationRequestJpaEntity.from(request)).toDomain();
        }
        ParticipationRequestJpaEntity entity = repository
                .findById(request.id().get().value())
                .orElseThrow(() -> new IllegalStateException("participation request to update must exist"));
        entity.update(request);
        return entity.toDomain();
    }

    @Override
    public Optional<ParticipationRequest> findPending(OrganizationId organizationId, AccountId accountId) {
        return repository
                .findByOrganizationIdAndAccountIdAndStatus(
                        organizationId.value(), accountId.value(), ParticipationRequestStatus.PENDING)
                .map(ParticipationRequestJpaEntity::toDomain);
    }
}
