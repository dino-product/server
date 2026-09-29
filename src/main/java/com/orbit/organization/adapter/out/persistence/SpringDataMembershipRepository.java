package com.orbit.organization.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataMembershipRepository extends JpaRepository<MembershipJpaEntity, Long> {
    Optional<MembershipJpaEntity> findByOrganizationIdAndAuthAccountId(Long organizationId, Long authAccountId);
}
