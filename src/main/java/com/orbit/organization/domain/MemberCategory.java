package com.orbit.organization.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 발주사의 직원 유형 또는 기사 유형. 삭제는 삭제 표시·삭제 시각을 남기는 소프트 삭제이고 복구하지 않는다. 같은 이름의 유형을 다시 만들면 새 유형이다.
 * 이름 중복은 같은 발주사·같은 종류의 삭제되지 않은 유형끼리만 따지며, 여러 유형에 걸친 규칙이라 Application이 확인한다.
 */
public final class MemberCategory {

    private final MemberCategoryId id;
    private final OrganizationId organizationId;
    private final MemberCategoryKind kind;
    private CategoryName name;
    private CategoryColor color;
    private boolean deleted;
    private Instant deletedAt;

    private MemberCategory(
            MemberCategoryId id,
            OrganizationId organizationId,
            MemberCategoryKind kind,
            CategoryName name,
            CategoryColor color,
            boolean deleted,
            Instant deletedAt) {
        this.id = id;
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId must not be null");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.color = Objects.requireNonNull(color, "color must not be null");
        if (deleted != (deletedAt != null)) {
            throw new IllegalArgumentException("deleted and deletedAt must be set together");
        }
        this.deleted = deleted;
        this.deletedAt = deletedAt;
    }

    public static MemberCategory create(
            OrganizationId organizationId, MemberCategoryKind kind, CategoryName name, CategoryColor color) {
        return new MemberCategory(null, organizationId, kind, name, color, false, null);
    }

    public static MemberCategory reconstitute(
            MemberCategoryId id,
            OrganizationId organizationId,
            MemberCategoryKind kind,
            CategoryName name,
            CategoryColor color,
            boolean deleted,
            Instant deletedAt) {
        return new MemberCategory(
                Objects.requireNonNull(id, "id must not be null"),
                organizationId,
                kind,
                name,
                color,
                deleted,
                deletedAt);
    }

    /** 이름·색상을 함께 교체한다. 삭제된 유형은 바꿀 수 없다. */
    public void change(CategoryName name, CategoryColor color) {
        CategoryName newName = Objects.requireNonNull(name, "name must not be null");
        CategoryColor newColor = Objects.requireNonNull(color, "color must not be null");
        if (deleted) {
            throw new IllegalStateException("deleted category cannot be changed");
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

    public Optional<MemberCategoryId> id() {
        return Optional.ofNullable(id);
    }

    public OrganizationId organizationId() {
        return organizationId;
    }

    public MemberCategoryKind kind() {
        return kind;
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
