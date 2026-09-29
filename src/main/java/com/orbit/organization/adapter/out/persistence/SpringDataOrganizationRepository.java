package com.orbit.organization.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataOrganizationRepository extends JpaRepository<OrganizationJpaEntity, Long> {}
