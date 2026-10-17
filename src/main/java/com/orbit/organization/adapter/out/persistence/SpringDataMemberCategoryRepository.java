package com.orbit.organization.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/** 직원 유형·기사 유형 저장소가 함께 쓰는 조회. 목록은 만든 순서(생성 시각, 같으면 식별자)다. */
@NoRepositoryBean
interface SpringDataMemberCategoryRepository<T extends MemberCategoryJpaEntity> extends JpaRepository<T, Long> {

    Optional<T> findByOrganizationIdAndNameAndDeletedFalse(Long organizationId, String name);

    List<T> findByOrganizationIdAndDeletedFalseOrderByCreatedAtAscIdAsc(Long organizationId);
}
