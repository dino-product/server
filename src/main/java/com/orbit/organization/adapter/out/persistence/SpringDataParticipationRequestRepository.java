package com.orbit.organization.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orbit.organization.domain.ParticipationRequestStatus;

interface SpringDataParticipationRequestRepository extends JpaRepository<ParticipationRequestJpaEntity, Long> {

    Optional<ParticipationRequestJpaEntity> findByOrganizationIdAndAccountIdAndStatus(
            Long organizationId, Long accountId, ParticipationRequestStatus status);
}
