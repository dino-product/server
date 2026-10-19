package com.orbit.organization.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 목록은 만든 순서(생성 시각, 같으면 식별자)다. */
interface SpringDataWorkTypeRepository extends JpaRepository<WorkTypeJpaEntity, Long> {

    Optional<WorkTypeJpaEntity> findByOrganizationIdAndNameAndDeletedFalse(Long organizationId, String name);

    List<WorkTypeJpaEntity> findByOrganizationIdAndDeletedFalseOrderByCreatedAtAscIdAsc(Long organizationId);
}
