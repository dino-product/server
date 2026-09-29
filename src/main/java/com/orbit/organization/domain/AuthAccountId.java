package com.orbit.organization.domain;

public record AuthAccountId(Long value) {
    public AuthAccountId {
        if (value == null || value <= 0) {
            throw new OrganizationRuleViolation("AuthAccountId must be positive");
        }
    }
}
