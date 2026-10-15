package com.orbit.organization.application.port.in.command.dto;

import java.time.Instant;

import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.ParticipationRequest;
import com.orbit.organization.domain.ParticipationRequestStatus;
import com.orbit.organization.domain.ParticipationType;

public record ParticipationRequestInfo(
        Long requestId,
        Long organizationId,
        String organizationName,
        ParticipationType desiredType,
        ParticipationRequestStatus status,
        Instant requestedAt) {

    public static ParticipationRequestInfo of(ParticipationRequest request, Organization organization) {
        return new ParticipationRequestInfo(
                request.id().orElseThrow().value(),
                request.organizationId().value(),
                organization.name().value(),
                request.desiredType(),
                request.status(),
                request.requestedAt());
    }
}
