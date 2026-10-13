package com.orbit.organization.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 계정의 발주사 기사 계약. 같은 계정·발주사의 계약은 1행이며, 역할 변경으로 다시 활성이 되면 같은 계약 ID를 쓴다. 기사 유형은 유형 지정 작업이 연결한다. */
public final class Technician {

    private final TechnicianId id;
    private final OrganizationId organizationId;
    private final AccountId accountId;
    private final TechnicianStatus status;
    private final Instant statusChangedAt;

    private Technician(
            TechnicianId id,
            OrganizationId organizationId,
            AccountId accountId,
            TechnicianStatus status,
            Instant statusChangedAt) {
        this.id = id;
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.statusChangedAt = Objects.requireNonNull(statusChangedAt, "statusChangedAt must not be null");
    }

    /** 참여 요청 승인으로 맺는 활성 기사 계약. 기사 유형은 "유형 없음"이다. */
    public static Technician join(OrganizationId organizationId, AccountId accountId, Instant joinedAt) {
        return new Technician(null, organizationId, accountId, TechnicianStatus.ACTIVE, joinedAt);
    }

    public static Technician reconstitute(
            TechnicianId id,
            OrganizationId organizationId,
            AccountId accountId,
            TechnicianStatus status,
            Instant statusChangedAt) {
        return new Technician(
                Objects.requireNonNull(id, "id must not be null"), organizationId, accountId, status, statusChangedAt);
    }

    public Optional<TechnicianId> id() {
        return Optional.ofNullable(id);
    }

    public OrganizationId organizationId() {
        return organizationId;
    }

    public AccountId accountId() {
        return accountId;
    }

    public TechnicianStatus status() {
        return status;
    }

    public Instant statusChangedAt() {
        return statusChangedAt;
    }
}
