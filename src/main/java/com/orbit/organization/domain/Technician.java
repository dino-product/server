package com.orbit.organization.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 계정의 발주사 기사 계약. 같은 계정·발주사의 계약은 1행이며, 다시 활성이 되면 같은 계약 ID를 쓴다. 기사 유형은 유형 지정 작업이 연결한다. */
public final class Technician {

    private final TechnicianId id;
    private final OrganizationId organizationId;
    private final AccountId accountId;
    private final TechnicianStatus status;
    private final Instant joinedAt;
    private final Instant statusChangedAt;
    private final Instant withdrawnAt;

    private Technician(
            TechnicianId id,
            OrganizationId organizationId,
            AccountId accountId,
            TechnicianStatus status,
            Instant joinedAt,
            Instant statusChangedAt,
            Instant withdrawnAt) {
        this.id = id;
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt must not be null");
        this.statusChangedAt = Objects.requireNonNull(statusChangedAt, "statusChangedAt must not be null");
        if (statusChangedAt.isBefore(joinedAt)) {
            throw new IllegalArgumentException("statusChangedAt must not be before joinedAt");
        }
        if (withdrawnAt != null && status != TechnicianStatus.DEACTIVATED) {
            throw new IllegalArgumentException("withdrawn technician must be deactivated");
        }
        this.withdrawnAt = withdrawnAt;
    }

    /** 참여 요청 승인으로 맺는 활성 기사 계약. 기사 유형은 "유형 없음"이고 참여일은 승인 시각이다. */
    public static Technician join(OrganizationId organizationId, AccountId accountId, Instant joinedAt) {
        return join(organizationId, accountId, joinedAt, joinedAt);
    }

    /** 같은 발주사에 직원으로 있던 회원을 기사로 승인해 새로 맺는 활성 기사 계약. 참여일은 이전 역할의 참여일을 이어받고 상태 변경 시각은 승인 시각이다. */
    public static Technician join(
            OrganizationId organizationId, AccountId accountId, Instant joinedAt, Instant activatedAt) {
        return new Technician(null, organizationId, accountId, TechnicianStatus.ACTIVE, joinedAt, activatedAt, null);
    }

    public static Technician reconstitute(
            TechnicianId id,
            OrganizationId organizationId,
            AccountId accountId,
            TechnicianStatus status,
            Instant joinedAt,
            Instant statusChangedAt) {
        return reconstitute(id, organizationId, accountId, status, joinedAt, statusChangedAt, null);
    }

    /** 탈퇴 표시까지 복원한다. {@code withdrawnAt}이 없으면 탈퇴로 비활성화되지 않은 계약이다. */
    public static Technician reconstitute(
            TechnicianId id,
            OrganizationId organizationId,
            AccountId accountId,
            TechnicianStatus status,
            Instant joinedAt,
            Instant statusChangedAt,
            Instant withdrawnAt) {
        return new Technician(
                Objects.requireNonNull(id, "id must not be null"),
                organizationId,
                accountId,
                status,
                joinedAt,
                statusChangedAt,
                withdrawnAt);
    }

    /**
     * 비활성 기사 계약을 참여 요청 승인으로 다시 활성으로 바꾼다. 계약 ID와 참여일은 유지한다([조직·계정] §6).
     *
     * @throws IllegalStateException 활성 계약이거나 탈퇴로 비활성화된 계약일 때
     */
    public Technician rejoin(Instant rejoinedAt) {
        if (status != TechnicianStatus.DEACTIVATED) {
            throw new IllegalStateException("only deactivated technicians can rejoin");
        }
        if (isWithdrawn()) {
            throw new IllegalStateException("withdrawn technicians cannot rejoin");
        }
        return new Technician(id, organizationId, accountId, TechnicianStatus.ACTIVE, joinedAt, rejoinedAt, null);
    }

    /**
     * 계정 탈퇴를 반영한다. 활성 계약은 비활성으로 바꾸고, 이미 비활성인 계약은 상태와 상태 변경 시각을 그대로 둔 채 탈퇴 표시만 남긴다. 탈퇴 표시가 있으면
     * 그대로 돌려준다(같은 탈퇴를 다시 받아도 결과가 같다). 탈퇴 표시가 있는 계약은 다시 활성이 되지 않는다([조직·계정] 기능명세 §6).
     */
    public Technician withdraw(Instant withdrawnAt) {
        Objects.requireNonNull(withdrawnAt, "withdrawnAt must not be null");
        if (isWithdrawn()) {
            return this;
        }
        if (status == TechnicianStatus.ACTIVE) {
            return new Technician(
                    id, organizationId, accountId, TechnicianStatus.DEACTIVATED, joinedAt, withdrawnAt, withdrawnAt);
        }
        return new Technician(id, organizationId, accountId, status, joinedAt, statusChangedAt, withdrawnAt);
    }

    public boolean isWithdrawn() {
        return withdrawnAt != null;
    }

    public Optional<Instant> withdrawnAt() {
        return Optional.ofNullable(withdrawnAt);
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
