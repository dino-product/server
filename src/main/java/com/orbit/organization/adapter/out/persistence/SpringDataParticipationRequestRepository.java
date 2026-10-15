package com.orbit.organization.adapter.out.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.orbit.organization.domain.ParticipationRequestStatus;

interface SpringDataParticipationRequestRepository extends JpaRepository<ParticipationRequestJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ParticipationRequestJpaEntity r where r.id = :id")
    Optional<ParticipationRequestJpaEntity> findByIdForUpdate(@Param("id") Long id);

    Optional<ParticipationRequestJpaEntity> findByOrganizationIdAndAccountIdAndStatus(
            Long organizationId, Long accountId, ParticipationRequestStatus status);
}
