package com.orbit.organization.domain;

public record OrganizationId(Long value) {

    public OrganizationId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("organizationId must be positive");
        }
    }
}
