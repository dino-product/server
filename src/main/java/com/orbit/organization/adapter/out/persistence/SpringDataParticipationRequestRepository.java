package com.orbit.organization.adapter.out.persistence;

import java.util.List;
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

    @Query("select distinct r.organizationId from ParticipationRequestJpaEntity r"
            + " where r.accountId = :accountId and r.status = :status order by r.organizationId")
    List<Long> findOrganizationIdsByAccountIdAndStatus(
            @Param("accountId") Long accountId, @Param("status") ParticipationRequestStatus status);
}
