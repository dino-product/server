package com.orbit.organization.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataStaffTypeRepository extends JpaRepository<StaffTypeJpaEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select staffType from StaffTypeJpaEntity staffType where staffType.id = :id")
    Optional<StaffTypeJpaEntity> findByIdForUpdate(@Param("id") Long id);

    List<StaffTypeJpaEntity> findAllByOrganizationIdOrderByIdAsc(Long organizationId);

    boolean existsByOrganizationIdAndName(Long organizationId, String name);

    boolean existsByOrganizationIdAndNameAndIdNot(Long organizationId, String name, Long id);
}
