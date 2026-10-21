package com.orbit.organization.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.orbit.organization.domain.MembershipStatus;

interface SpringDataMembershipRepository extends JpaRepository<MembershipJpaEntity, Long> {

    Optional<MembershipJpaEntity> findByOrganizationIdAndAccountId(Long organizationId, Long accountId);

    Optional<MembershipJpaEntity> findByOrganizationIdAndAccountIdAndStatus(
            Long organizationId, Long accountId, MembershipStatus status);

    boolean existsByOrganizationIdAndAccountIdAndStatus(Long organizationId, Long accountId, MembershipStatus status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MembershipJpaEntity m set m.staffCategoryId = null, m.updatedAt = :at"
            + " where m.organizationId = :organizationId and m.staffCategoryId = :categoryId")
    int unassignStaffCategory(
            @Param("organizationId") Long organizationId,
            @Param("categoryId") Long categoryId,
            @Param("at") Instant at);

    @Query("select m.organizationId from MembershipJpaEntity m"
            + " where m.accountId = :accountId and m.owner = true and m.status = :status order by m.organizationId")
    List<Long> findOwnedOrganizationIds(@Param("accountId") Long accountId, @Param("status") MembershipStatus status);

    @Query("select distinct m.organizationId from MembershipJpaEntity m where m.accountId = :accountId"
            + " order by m.organizationId")
    List<Long> findOrganizationIdsByAccountId(@Param("accountId") Long accountId);

    long countByOrganizationIdAndOwnerTrueAndStatus(Long organizationId, MembershipStatus status);
}
