package com.orbit.organization.application.port.in.query.dto;

/** {@code pendingRequestId}는 {@link ParticipationAvailability#PENDING_REQUEST}일 때만 있다. */
public record CompanyCheckInfo(
        Long organizationId, String organizationName, ParticipationAvailability availability, Long pendingRequestId) {}
