package com.orbit.organization.domain;

import java.time.Instant;

/** 참여 요청(participant_request). 승인과 실제 직원 소속·기사 계약 생성의 원자성은 호출자가 보장한다. */
public final class ParticipantRequest {
    private final ParticipantRequestId id;
    private final OrganizationId organizationId;
    private final AuthAccountId authAccountId;
    private final ParticipantType requestedType;
    private final RequestChannel channel;
    private final Instant requestedAt;
    private ParticipantRequestStatus status = ParticipantRequestStatus.PENDING;
    private ParticipantType confirmedType;
    private Instant processedAt;
    private String rejectionReason;

    private ParticipantRequest(
            ParticipantRequestId id,
            OrganizationId organizationId,
            AuthAccountId authAccountId,
            ParticipantType requestedType,
            RequestChannel channel,
            Instant requestedAt) {
        if (id == null
                || organizationId == null
                || authAccountId == null
                || requestedType == null
                || channel == null
                || requestedAt == null) {
            throw new OrganizationRuleViolation("request fields must not be null");
        }
        this.id = id;
        this.organizationId = organizationId;
        this.authAccountId = authAccountId;
        this.requestedType = requestedType;
        this.channel = channel;
        this.requestedAt = requestedAt;
    }

    public static ParticipantRequest create(
            ParticipantRequestId id,
            OrganizationId organizationId,
            AuthAccountId authAccountId,
            ParticipantType requestedType,
            RequestChannel channel,
            Instant requestedAt) {
        return new ParticipantRequest(id, organizationId, authAccountId, requestedType, channel, requestedAt);
    }

    public void approve(ParticipantType confirmedType, Instant approvedAt) {
        requirePending(approvedAt);
        if (confirmedType == null) {
            throw new OrganizationRuleViolation("confirmedType must not be null");
        }
        this.confirmedType = confirmedType;
        this.processedAt = approvedAt;
        this.status = ParticipantRequestStatus.APPROVED;
    }

    public void reject(String reason, Instant rejectedAt) {
        requirePending(rejectedAt);
        this.rejectionReason = reason == null || reason.isBlank() ? null : reason;
        this.processedAt = rejectedAt;
        this.status = ParticipantRequestStatus.REJECTED;
    }

    public void cancel(Instant cancelledAt) {
        requirePending(cancelledAt);
        this.processedAt = cancelledAt;
        this.status = ParticipantRequestStatus.CANCELLED;
    }

    private void requirePending(Instant processedAt) {
        if (status != ParticipantRequestStatus.PENDING) {
            throw new OrganizationRuleViolation("only a pending request can be processed");
        }
        if (processedAt == null || processedAt.isBefore(requestedAt)) {
            throw new OrganizationRuleViolation("processing time must not precede request time");
        }
    }

    public ParticipantRequestId id() {
        return id;
    }

    public OrganizationId organizationId() {
        return organizationId;
    }

    public AuthAccountId authAccountId() {
        return authAccountId;
    }

    public ParticipantType requestedType() {
        return requestedType;
    }

    public RequestChannel channel() {
        return channel;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public ParticipantRequestStatus status() {
        return status;
    }

    public ParticipantType confirmedType() {
        return confirmedType;
    }

    public Instant processedAt() {
        return processedAt;
    }

    public String rejectionReason() {
        return rejectionReason;
    }
}
