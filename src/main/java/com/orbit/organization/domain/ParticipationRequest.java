package com.orbit.organization.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 계정이 회사 코드로 확인한 발주사에 보내는 참여 요청. 승인·거절·취소 뒤 다시 요청하면 이 요청을 되살리지 않고 새 요청을 만든다. 확정 유형과 처리 시각은 승인·거절
 * 때만, 거절 사유는 거절 때만 있다.
 */
public final class ParticipationRequest {

    private final ParticipationRequestId id;
    private final OrganizationId organizationId;
    private final AccountId accountId;
    private final ParticipationType desiredType;
    private final ParticipationType confirmedType;
    private ParticipationRequestStatus status;
    private final String rejectReason;
    private final Instant requestedAt;
    private final Instant decidedAt;
    private Instant updatedAt;

    private ParticipationRequest(
            ParticipationRequestId id,
            OrganizationId organizationId,
            AccountId accountId,
            ParticipationType desiredType,
            ParticipationType confirmedType,
            ParticipationRequestStatus status,
            String rejectReason,
            Instant requestedAt,
            Instant decidedAt,
            Instant updatedAt) {
        this.id = id;
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.desiredType = Objects.requireNonNull(desiredType, "desiredType must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        this.updatedAt = requireNotBeforeRequest(updatedAt);
        boolean decided =
                status == ParticipationRequestStatus.APPROVED || status == ParticipationRequestStatus.REJECTED;
        if (decided != (decidedAt != null)) {
            throw new IllegalArgumentException("decidedAt must exist only for approved or rejected requests");
        }
        if ((status == ParticipationRequestStatus.APPROVED) != (confirmedType != null)) {
            throw new IllegalArgumentException("confirmedType must exist only for approved requests");
        }
        if (rejectReason != null && status != ParticipationRequestStatus.REJECTED) {
            throw new IllegalArgumentException("rejectReason must exist only for rejected requests");
        }
        this.confirmedType = confirmedType;
        this.rejectReason = rejectReason;
        this.decidedAt = decidedAt == null ? null : requireNotBeforeRequest(decidedAt);
    }

    /** 승인 대기 요청을 새로 만든다. */
    public static ParticipationRequest request(
            OrganizationId organizationId, AccountId accountId, ParticipationType desiredType, Instant requestedAt) {
        return new ParticipationRequest(
                null,
                organizationId,
                accountId,
                desiredType,
                null,
                ParticipationRequestStatus.PENDING,
                null,
                requestedAt,
                null,
                requestedAt);
    }

    public static ParticipationRequest reconstitute(
            ParticipationRequestId id,
            OrganizationId organizationId,
            AccountId accountId,
            ParticipationType desiredType,
            ParticipationType confirmedType,
            ParticipationRequestStatus status,
            String rejectReason,
            Instant requestedAt,
            Instant decidedAt,
            Instant updatedAt) {
        return new ParticipationRequest(
                Objects.requireNonNull(id, "id must not be null"),
                organizationId,
                accountId,
                desiredType,
                confirmedType,
                status,
                rejectReason,
                requestedAt,
                decidedAt,
                updatedAt);
    }

    /** 요청자가 승인 대기 중인 요청을 거둬들인다. 처리 시각은 승인·거절에만 쓰므로 남기지 않는다. */
    public void cancel(Instant canceledAt) {
        if (!isPending()) {
            throw new IllegalStateException("only pending requests can be canceled");
        }
        Instant newUpdatedAt = requireNotBeforeRequest(canceledAt);
        this.status = ParticipationRequestStatus.CANCELED;
        this.updatedAt = newUpdatedAt;
    }

    public boolean isRequestedBy(AccountId requester) {
        return accountId.equals(requester);
    }

    public boolean isPending() {
        return status == ParticipationRequestStatus.PENDING;
    }

    private Instant requireNotBeforeRequest(Instant at) {
        Objects.requireNonNull(at, "at must not be null");
        if (at.isBefore(requestedAt)) {
            throw new IllegalArgumentException("time must not be before requestedAt");
        }
        return at;
    }

    public Optional<ParticipationRequestId> id() {
        return Optional.ofNullable(id);
    }

    public OrganizationId organizationId() {
        return organizationId;
    }

    public AccountId accountId() {
        return accountId;
    }

    public ParticipationType desiredType() {
        return desiredType;
    }

    public Optional<ParticipationType> confirmedType() {
        return Optional.ofNullable(confirmedType);
    }

    public ParticipationRequestStatus status() {
        return status;
    }

    public Optional<String> rejectReason() {
        return Optional.ofNullable(rejectReason);
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public Optional<Instant> decidedAt() {
        return Optional.ofNullable(decidedAt);
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
