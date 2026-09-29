package com.orbit.organization.domain;

import java.time.Instant;

/** 회사와 외부 계정 사이의 관계. 다른 종류의 관계로 전환하지 않는다. */
public final class Membership {
    private final MembershipId id;
    private final OrganizationId organizationId;
    private final AuthAccountId authAccountId;
    private final Instant joinedAt;
    private StaffTypeId typeId;
    private boolean active;

    private Membership(
            MembershipId id,
            OrganizationId organizationId,
            AuthAccountId authAccountId,
            StaffType type,
            Instant joinedAt) {
        this(id, organizationId, authAccountId, null, joinedAt, true);
        changeType(type);
    }

    private Membership(
            MembershipId id,
            OrganizationId organizationId,
            AuthAccountId authAccountId,
            StaffTypeId typeId,
            Instant joinedAt,
            boolean active) {
        if (id == null || organizationId == null || authAccountId == null || joinedAt == null) {
            throw new OrganizationRuleViolation("relationship fields must not be null");
        }
        this.id = id;
        this.organizationId = organizationId;
        this.authAccountId = authAccountId;
        this.typeId = typeId;
        this.joinedAt = joinedAt;
        this.active = active;
    }

    public static Membership create(
            MembershipId id,
            OrganizationId organizationId,
            AuthAccountId authAccountId,
            StaffType type,
            Instant joinedAt) {
        return new Membership(id, organizationId, authAccountId, type, joinedAt);
    }

    /** 저장된 유형 ID와 활성 상태를 보존하고 현재 유형 정책은 다시 검사하지 않는다. */
    public static Membership reconstitute(
            MembershipId id,
            OrganizationId organizationId,
            AuthAccountId authAccountId,
            StaffTypeId typeId,
            Instant joinedAt,
            boolean active) {
        return new Membership(id, organizationId, authAccountId, typeId, joinedAt, active);
    }

    public void changeType(StaffType type) {
        if (type != null && (!organizationId.equals(type.organizationId()) || !type.active())) {
            throw new OrganizationRuleViolation("type must be active and belong to the same organization");
        }
        typeId = type == null ? null : type.id();
    }

    public void activate() {
        active = true;
    }
    /** 총관리자 보존을 검사하는 조직 도메인에서만 호출한다. */
    void deactivate() {
        active = false;
    }

    public MembershipId id() {
        return id;
    }

    public OrganizationId organizationId() {
        return organizationId;
    }

    public AuthAccountId authAccountId() {
        return authAccountId;
    }

    public Instant joinedAt() {
        return joinedAt;
    }

    public StaffTypeId typeId() {
        return typeId;
    }

    public boolean active() {
        return active;
    }
}
