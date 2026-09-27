package com.orbit.organization.domain;

public record ParticipantRequestId(Long value) {
    public ParticipantRequestId {
        if (value == null || value <= 0) {
            throw new OrganizationRuleViolation("ParticipantRequestId must be positive");
        }
    }
}
