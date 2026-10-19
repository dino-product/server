package com.orbit.organization.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataTechnicianRepository extends JpaRepository<TechnicianJpaEntity, Long> {

    Optional<TechnicianJpaEntity> findByOrganizationIdAndAccountId(Long organizationId, Long accountId);

    @Query("select distinct t.organizationId from TechnicianJpaEntity t where t.accountId = :accountId"
            + " order by t.organizationId")
    List<Long> findOrganizationIdsByAccountId(@Param("accountId") Long accountId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TechnicianJpaEntity t set t.technicianCategoryId = null, t.updatedAt = :at"
            + " where t.organizationId = :organizationId and t.technicianCategoryId = :categoryId")
    int unassignTechnicianCategory(
            @Param("organizationId") Long organizationId,
            @Param("categoryId") Long categoryId,
            @Param("at") Instant at);
}
