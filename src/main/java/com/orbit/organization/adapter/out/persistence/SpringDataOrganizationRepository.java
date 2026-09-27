package com.orbit.organization.adapter.out.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataOrganizationRepository extends JpaRepository<OrganizationJpaEntity, Long> {
    boolean existsByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select organization from OrganizationJpaEntity organization where organization.id = :id")
    Optional<OrganizationJpaEntity> findByIdForUpdate(@Param("id") Long id);
}
