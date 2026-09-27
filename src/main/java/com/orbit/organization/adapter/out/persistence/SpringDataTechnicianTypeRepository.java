package com.orbit.organization.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataTechnicianTypeRepository extends JpaRepository<TechnicianTypeJpaEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select technicianType from TechnicianTypeJpaEntity technicianType where technicianType.id = :id")
    Optional<TechnicianTypeJpaEntity> findByIdForUpdate(@Param("id") Long id);

    List<TechnicianTypeJpaEntity> findAllByOrganizationIdOrderByIdAsc(Long organizationId);

    boolean existsByOrganizationIdAndName(Long organizationId, String name);

    boolean existsByOrganizationIdAndNameAndIdNot(Long organizationId, String name, Long id);
}
