package com.orbit.organization.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orbit.organization.domain.MembershipStatus;

interface SpringDataMembershipRepository extends JpaRepository<MembershipJpaEntity, Long> {

    Optional<MembershipJpaEntity> findByOrganizationIdAndAccountIdAndStatus(
            Long organizationId, Long accountId, MembershipStatus status);
}
