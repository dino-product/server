package com.orbit.organization.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 계정이 회사 코드로 확인한 발주사에 보내는 참여 요청. 승인·거절·취소 뒤 다시 요청하면 이 요청을 되살리지 않고 새 요청을 만든다. 처리한 총관리자 소속과 처리 시각은
 * 승인·거절 때만, 확정 유형은 승인 때만, 거절 사유는 거절 때만 있다.
 */
public final class ParticipationRequest {

    private final ParticipationRequestId id;
    private final OrganizationId organizationId;
    private final AccountId accountId;
    private final ParticipationType desiredType;
    private final RequestChannel requestChannel;
    private ParticipationType confirmedType;
    private ParticipationRequestStatus status;
    private String rejectReason;
    private final Instant requestedAt;
    private Instant decidedAt;
    private MembershipId decidedBy;

    private ParticipationRequest(
            ParticipationRequestId id,
            OrganizationId organizationId,
            AccountId accountId,
            ParticipationType desiredType,
            RequestChannel requestChannel,
            ParticipationType confirmedType,
            ParticipationRequestStatus status,
            String rejectReason,
            Instant requestedAt,
            Instant decidedAt,
            MembershipId decidedBy) {
        this.id = id;
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.desiredType = Objects.requireNonNull(desiredType, "desiredType must not be null");
        this.requestChannel = Objects.requireNonNull(requestChannel, "requestChannel must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        boolean decided =
                status == ParticipationRequestStatus.APPROVED || status == ParticipationRequestStatus.REJECTED;
        if (decided != (decidedAt != null) || decided != (decidedBy != null)) {
            throw new IllegalArgumentException(
                    "decidedAt and decidedBy must exist only for approved or rejected requests");
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
        this.decidedBy = decidedBy;
    }

    /** 요청자가 회사를 찾은 경로와 함께 승인 대기 요청을 새로 만든다. */
    public static ParticipationRequest request(
            OrganizationId organizationId,
            AccountId accountId,
            ParticipationType desiredType,
            RequestChannel requestChannel,
            Instant requestedAt) {
        return new ParticipationRequest(
                null,
                organizationId,
                accountId,
                desiredType,
                requestChannel,
                null,
                ParticipationRequestStatus.PENDING,
                null,
                requestedAt,
                null,
                null);
    }

    public static ParticipationRequest reconstitute(
            ParticipationRequestId id,
            OrganizationId organizationId,
            AccountId accountId,
            ParticipationType desiredType,
            RequestChannel requestChannel,
            ParticipationType confirmedType,
            ParticipationRequestStatus status,
            String rejectReason,
            Instant requestedAt,
            Instant decidedAt,
            MembershipId decidedBy) {
        return new ParticipationRequest(
                Objects.requireNonNull(id, "id must not be null"),
                organizationId,
                accountId,
                desiredType,
                requestChannel,
                confirmedType,
                status,
                rejectReason,
                requestedAt,
                decidedAt,
                decidedBy);
    }

    /** 요청자가 승인 대기 중인 요청을 거둬들인다. 처리 시각은 승인·거절에만 쓰므로 남기지 않는다. */
    public void cancel() {
        if (!isPending()) {
            throw new IllegalStateException("only pending requests can be canceled");
        }
        this.status = ParticipationRequestStatus.CANCELED;
    }

    /**
     * 총관리자가 확정 유형으로 승인한다. 확정 유형은 희망 유형과 달라도 된다. 소속·기사 계약은 서비스가 같은 트랜잭션에서 만든다.
     *
     * @throws IllegalStateException 승인 대기가 아닐 때
     * @throws IllegalArgumentException 처리 시각이 요청 시각보다 앞설 때
     */
    public void approve(ParticipationType confirmedType, MembershipId decider, Instant at) {
        Objects.requireNonNull(confirmedType, "confirmedType must not be null");
        decide(decider, at);
        this.confirmedType = confirmedType;
        this.status = ParticipationRequestStatus.APPROVED;
    }

    /**
     * 총관리자가 거절한다. 사유는 선택이다.
     *
     * @throws IllegalStateException 승인 대기가 아닐 때
     * @throws IllegalArgumentException 처리 시각이 요청 시각보다 앞설 때
     */
    public void reject(Optional<RejectReason> reason, MembershipId decider, Instant at) {
        Objects.requireNonNull(reason, "reason must not be null");
        decide(decider, at);
        this.rejectReason = reason.map(RejectReason::value).orElse(null);
        this.status = ParticipationRequestStatus.REJECTED;
    }

    /** 승인일 때 확정 유형이 희망 유형과 같은지. 승인되지 않았으면 거짓이다. */
    public boolean isConfirmedAsDesired() {
        return desiredType == confirmedType;
    }

    private void decide(MembershipId decider, Instant at) {
        Objects.requireNonNull(decider, "decider must not be null");
        if (!isPending()) {
            throw new IllegalStateException("only pending requests can be decided");
        }
        this.decidedAt = requireNotBeforeRequest(at);
        this.decidedBy = decider;
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

    public RequestChannel requestChannel() {
        return requestChannel;
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

    public Optional<MembershipId> decidedBy() {
        return Optional.ofNullable(decidedBy);
    }
}
