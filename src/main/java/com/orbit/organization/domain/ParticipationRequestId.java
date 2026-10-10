package com.orbit.organization.domain;

public record ParticipationRequestId(Long value) {

    public ParticipationRequestId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("participationRequestId must be positive");
        }
    }
}
