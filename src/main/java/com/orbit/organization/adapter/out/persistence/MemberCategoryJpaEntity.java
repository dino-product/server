package com.orbit.organization.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import com.orbit.organization.domain.CategoryColor;
import com.orbit.organization.domain.CategoryName;
import com.orbit.organization.domain.MemberCategory;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.organization.domain.OrganizationId;

/**
 * 직원 유형·기사 유형 테이블의 공통 컬럼. 두 종류는 테이블이 달라 식별자가 따로 매겨진다. 발주사는 ID로만 둔다. 각 테이블은 삭제되지 않은 행끼리만 (발주사, 이름)이
 * 유일한 부분 유일 인덱스를 둔다. JPA 매핑으로는 조건부 유일 인덱스를 만들 수 없어 {@code db/partial-unique-indexes.sql}이 정의한다. 정상 경로는 발주사
 * 잠금으로 먼저 거르고, 인덱스는 잠금을 거치지 않은 저장의 최종 방어선이다.
 */
@MappedSuperclass
abstract class MemberCategoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long organizationId;

    @Column(name = "name", nullable = false, length = CategoryName.MAX_LENGTH)
    private String name;

    @Column(name = "color", nullable = false, length = CategoryColor.LENGTH)
    private String color;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected MemberCategoryJpaEntity() {}

    protected MemberCategoryJpaEntity(MemberCategory category) {
        if (category.id().isPresent()) {
            throw new IllegalArgumentException("only new categories can be saved");
        }
        this.organizationId = category.organizationId().value();
        this.createdAt = category.createdAt();
        apply(category);
    }

    abstract MemberCategoryKind kind();

    /** 생성 뒤 바뀌는 값(이름·색상·삭제 표시)만 옮긴다. */
    final void apply(MemberCategory category) {
        this.name = category.name().value();
        this.color = category.color().value();
        this.deleted = category.isDeleted();
        this.updatedAt = category.updatedAt();
        this.deletedAt = category.deletedAt().orElse(null);
    }

    final MemberCategory toDomain() {
        return MemberCategory.reconstitute(
                new MemberCategoryId(id),
                new OrganizationId(organizationId),
                kind(),
                new CategoryName(name),
                new CategoryColor(color),
                createdAt,
                updatedAt,
                deleted,
                deletedAt);
    }
}
