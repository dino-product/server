package com.orbit.organization.adapter.out.persistence;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataTechnicianRepository extends JpaRepository<TechnicianJpaEntity, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TechnicianJpaEntity t set t.technicianCategoryId = null, t.updatedAt = :at"
            + " where t.organizationId = :organizationId and t.technicianCategoryId = :categoryId")
    int unassignTechnicianCategory(
            @Param("organizationId") Long organizationId,
            @Param("categoryId") Long categoryId,
            @Param("at") Instant at);
}
