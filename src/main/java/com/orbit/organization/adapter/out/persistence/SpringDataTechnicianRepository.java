package com.orbit.organization.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orbit.organization.domain.TechnicianStatus;

interface SpringDataTechnicianRepository extends JpaRepository<TechnicianJpaEntity, Long> {

    Optional<TechnicianJpaEntity> findByOrganizationIdAndAccountIdAndStatus(
            Long organizationId, Long accountId, TechnicianStatus status);
}
