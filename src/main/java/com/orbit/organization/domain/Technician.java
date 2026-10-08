package com.orbit.organization.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 계정이 발주사와 맺은 기사 계약. 직원 소속과 다른 애그리게잇이며, 역할 변경으로 끝났다가 다시 활성이 되어도 같은 ID를 써서 작업 배정·이력이 이어진다. 계약을
 * 만들고 상태를 바꾸는 행위는 참여 요청 승인·비활성화·역할 변경 유즈케이스와 함께 추가한다.
 */
public final class Technician {

    private final TechnicianId id;
    private final OrganizationId organizationId;
    private final AccountId accountId;
    private final TechnicianStatus status;
    private final Instant contractedAt;
    private final Instant statusChangedAt;

    private Technician(
            TechnicianId id,
            OrganizationId organizationId,
            AccountId accountId,
            TechnicianStatus status,
            Instant contractedAt,
            Instant statusChangedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.contractedAt = Objects.requireNonNull(contractedAt, "contractedAt must not be null");
        this.statusChangedAt = Objects.requireNonNull(statusChangedAt, "statusChangedAt must not be null");
        if (statusChangedAt.isBefore(contractedAt)) {
            throw new IllegalArgumentException("statusChangedAt must not be before contractedAt");
        }
    }

    public static Technician reconstitute(
            TechnicianId id,
            OrganizationId organizationId,
            AccountId accountId,
            TechnicianStatus status,
            Instant contractedAt,
            Instant statusChangedAt) {
        return new Technician(id, organizationId, accountId, status, contractedAt, statusChangedAt);
    }

    public TechnicianId id() {
        return id;
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

    public Instant contractedAt() {
        return contractedAt;
    }

    public Instant statusChangedAt() {
        return statusChangedAt;
    }
}
