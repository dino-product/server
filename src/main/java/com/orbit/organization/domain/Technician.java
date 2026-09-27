package com.orbit.organization.domain;

import java.time.Instant;

/** 회사와 외부 계정 사이의 관계. 다른 종류의 관계로 전환하지 않는다. */
public final class Technician {
    private final TechnicianId id;
    private final OrganizationId organizationId;
    private final AuthAccountId authAccountId;
    private final Instant contractedAt;
    private TechnicianTypeId typeId;
    private boolean active = true;

    private Technician(
            TechnicianId id,
            OrganizationId organizationId,
            AuthAccountId authAccountId,
            TechnicianType type,
            Instant contractedAt) {
        if (id == null || organizationId == null || authAccountId == null || contractedAt == null) {
            throw new OrganizationRuleViolation("relationship fields must not be null");
        }
        this.id = id;
        this.organizationId = organizationId;
        this.authAccountId = authAccountId;
        this.contractedAt = contractedAt;
        changeType(type);
    }

    public static Technician create(
            TechnicianId id,
            OrganizationId organizationId,
            AuthAccountId authAccountId,
            TechnicianType type,
            Instant contractedAt) {
        return new Technician(id, organizationId, authAccountId, type, contractedAt);
    }

    public void changeType(TechnicianType type) {
        if (type != null && (!organizationId.equals(type.organizationId()) || !type.active())) {
            throw new OrganizationRuleViolation("type must be active and belong to the same organization");
        }
        typeId = type == null ? null : type.id();
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    public TechnicianId id() {
        return id;
    }

    public OrganizationId organizationId() {
        return organizationId;
    }

    public AuthAccountId authAccountId() {
        return authAccountId;
    }

    public Instant contractedAt() {
        return contractedAt;
    }

    public TechnicianTypeId typeId() {
        return typeId;
    }

    public boolean active() {
        return active;
    }
}
