package com.orbit.organization.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.orbit.organization.domain.CategoryColor;
import com.orbit.organization.domain.CategoryName;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.WorkType;
import com.orbit.organization.domain.WorkTypeId;
import com.orbit.shared.persistence.BaseTimeEntity;

/**
 * 작업 유형 테이블(팀 ERD의 작업 유형). 발주사는 ID로만 둔다. schedule의 작업이 {@code work.job_type_id}로 이 행을 참조하며, 삭제해도 행을 남겨 작업중·완료·취소
 * 작업이 삭제 직전 이름·색상을 계속 보여 준다. 삭제되지 않은 행끼리만 (발주사, 이름)이 유일한 부분 유일 인덱스는 JPA 매핑으로 만들 수 없어
 * {@code db/partial-unique-indexes.sql}이 정의한다.
 */
@Entity
@Table(name = "work_type")
class WorkTypeJpaEntity extends BaseTimeEntity {

    /** 삭제되지 않은 행끼리 (발주사, 이름)이 유일한 부분 유일 인덱스. 정의는 {@code db/partial-unique-indexes.sql}에 있다. */
    static final String UNDELETED_NAME_INDEX = "uk_work_type_undeleted_name";

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

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected WorkTypeJpaEntity() {}

    WorkTypeJpaEntity(WorkType workType) {
        if (workType.id().isPresent()) {
            throw new IllegalArgumentException("only new work types can be saved");
        }
        this.organizationId = workType.organizationId().value();
        apply(workType);
    }

    /** 생성 뒤 바뀌는 값(이름·색상·삭제 표시)만 옮긴다. */
    void apply(WorkType workType) {
        this.name = workType.name().value();
        this.color = workType.color().value();
        this.deleted = workType.isDeleted();
        this.deletedAt = workType.deletedAt().orElse(null);
    }

    WorkType toDomain() {
        return WorkType.reconstitute(
                new WorkTypeId(id),
                new OrganizationId(organizationId),
                new CategoryName(name),
                new CategoryColor(color),
                deleted,
                deletedAt);
    }
}
