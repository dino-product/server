package com.orbit.organization.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 발주사의 작업 유형. 이름·색상 규칙과 소프트 삭제는 직원·기사 유형({@link MemberCategory})과 같지만, 삭제해도 작업중·완료·취소 작업이 이 유형을 계속
 * 참조해 삭제 직전 이름·색상을 보여 주므로 삭제 뒤에도 행을 남기고 이름·색상을 지우지 않는다. 같은 이름의 유형을 다시 만들면 새 유형이다. 이름 중복은 같은
 * 발주사의 삭제되지 않은 작업 유형끼리만 따지며, 여러 유형에 걸친 규칙이라 Application이 확인한다.
 */
public final class WorkType {

    private final WorkTypeId id;
    private final OrganizationId organizationId;
    private CategoryName name;
    private CategoryColor color;
    private boolean deleted;
    private Instant deletedAt;

    private WorkType(
            WorkTypeId id,
            OrganizationId organizationId,
            CategoryName name,
            CategoryColor color,
            boolean deleted,
            Instant deletedAt) {
        this.id = id;
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.color = Objects.requireNonNull(color, "color must not be null");
        if (deleted != (deletedAt != null)) {
            throw new IllegalArgumentException("deleted and deletedAt must be set together");
        }
        this.deleted = deleted;
        this.deletedAt = deletedAt;
    }

    public static WorkType create(OrganizationId organizationId, CategoryName name, CategoryColor color) {
        return new WorkType(null, organizationId, name, color, false, null);
    }

    public static WorkType reconstitute(
            WorkTypeId id,
            OrganizationId organizationId,
            CategoryName name,
            CategoryColor color,
            boolean deleted,
            Instant deletedAt) {
        return new WorkType(
                Objects.requireNonNull(id, "id must not be null"), organizationId, name, color, deleted, deletedAt);
    }

    /** 이름·색상을 함께 교체한다. 삭제된 유형은 바꿀 수 없다. */
    public void change(CategoryName name, CategoryColor color) {
        CategoryName newName = Objects.requireNonNull(name, "name must not be null");
        CategoryColor newColor = Objects.requireNonNull(color, "color must not be null");
        if (deleted) {
            throw new IllegalStateException("deleted work type cannot be changed");
        }
        this.name = newName;
        this.color = newColor;
    }

    /** 소프트 삭제한다. 이미 삭제됐으면 처음 삭제 시각을 그대로 두고 {@code false}를 돌려준다(재삭제 멱등). */
    public boolean delete(Instant deletedAt) {
        Instant at = Objects.requireNonNull(deletedAt, "deletedAt must not be null");
        if (deleted) {
            return false;
        }
        this.deleted = true;
        this.deletedAt = at;
        return true;
    }

    public Optional<WorkTypeId> id() {
        return Optional.ofNullable(id);
    }

    public OrganizationId organizationId() {
        return organizationId;
    }

    public CategoryName name() {
        return name;
    }

    public CategoryColor color() {
        return color;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Optional<Instant> deletedAt() {
        return Optional.ofNullable(deletedAt);
    }
}
