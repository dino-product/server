package com.orbit.organization.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 목록은 만든 순서(생성 시각, 같으면 식별자)다. */
interface SpringDataWorkTypeRepository extends JpaRepository<WorkTypeJpaEntity, Long> {

    Optional<WorkTypeJpaEntity> findByOrganizationIdAndNameAndDeletedFalse(Long organizationId, String name);

    List<WorkTypeJpaEntity> findByOrganizationIdAndDeletedFalseOrderByCreatedAtAscIdAsc(Long organizationId);

    List<WorkTypeJpaEntity> findByOrganizationIdAndIdIn(Long organizationId, Collection<Long> ids);

    /** 삭제되지 않은 그 발주사 유형이면 행을 공유 잠금(FOR SHARE)으로 잡는다. 삭제(행 UPDATE)와 서로 기다린다. */
    @Query(
            value = "select id from work_type where id = :id and company_id = :organizationId and is_deleted = false"
                    + " for share",
            nativeQuery = true)
    Optional<Long> lockUndeleted(Long organizationId, Long id);
}
