package com.orbit.organization.domain;

public record MembershipId(Long value) {
    public MembershipId {
        if (value == null || value <= 0) {
            throw new OrganizationRuleViolation("MembershipId must be positive");
        }
    }
}
