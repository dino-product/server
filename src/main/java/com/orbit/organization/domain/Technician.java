package com.orbit.organization.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 계정의 발주사 기사 계약. 같은 계정·발주사의 계약은 1행이며, 다시 활성이 되면 같은 계약 ID를 쓴다. 기사 유형은 계약 도메인에 두지 않고 저장소가 따로 바꾼다. */
public final class Technician {

    private final TechnicianId id;
    private final OrganizationId organizationId;
    private final AccountId accountId;
    private final TechnicianStatus status;
    private final Instant joinedAt;
    private final Instant statusChangedAt;

    private Technician(
            TechnicianId id,
            OrganizationId organizationId,
            AccountId accountId,
            TechnicianStatus status,
            Instant joinedAt,
            Instant statusChangedAt) {
        this.id = id;
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt must not be null");
        this.statusChangedAt = Objects.requireNonNull(statusChangedAt, "statusChangedAt must not be null");
        if (statusChangedAt.isBefore(joinedAt)) {
            throw new IllegalArgumentException("statusChangedAt must not be before joinedAt");
        }
    }

    /** 참여 요청 승인으로 맺는 활성 기사 계약. 기사 유형은 "유형 없음"이고 참여일은 승인 시각이다. */
    public static Technician join(OrganizationId organizationId, AccountId accountId, Instant joinedAt) {
        return join(organizationId, accountId, joinedAt, joinedAt);
    }

    /** 같은 발주사에 직원으로 있던 회원을 기사로 승인해 새로 맺는 활성 기사 계약. 참여일은 이전 역할의 참여일을 이어받고 상태 변경 시각은 승인 시각이다. */
    public static Technician join(
            OrganizationId organizationId, AccountId accountId, Instant joinedAt, Instant activatedAt) {
        return new Technician(null, organizationId, accountId, TechnicianStatus.ACTIVE, joinedAt, activatedAt);
    }

    public static Technician reconstitute(
            TechnicianId id,
            OrganizationId organizationId,
            AccountId accountId,
            TechnicianStatus status,
            Instant joinedAt,
            Instant statusChangedAt) {
        return new Technician(
                Objects.requireNonNull(id, "id must not be null"),
                organizationId,
                accountId,
                status,
                joinedAt,
                statusChangedAt);
    }

    /**
     * 비활성 기사 계약을 참여 요청 승인으로 다시 활성으로 바꾼다. 계약 ID와 참여일은 유지한다([조직·계정] §6).
     *
     * @throws IllegalStateException 활성 계약일 때
     */
    public Technician rejoin(Instant rejoinedAt) {
        if (status != TechnicianStatus.DEACTIVATED) {
            throw new IllegalStateException("only deactivated technicians can rejoin");
        }
        return new Technician(id, organizationId, accountId, TechnicianStatus.ACTIVE, joinedAt, rejoinedAt);
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

    public Instant joinedAt() {
        return joinedAt;
    }

    public Instant statusChangedAt() {
        return statusChangedAt;
    }
}
