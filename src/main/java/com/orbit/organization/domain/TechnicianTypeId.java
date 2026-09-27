package com.orbit.organization.domain;

public record TechnicianTypeId(Long value) {
    public TechnicianTypeId {
        if (value == null || value <= 0) {
            throw new OrganizationRuleViolation("TechnicianTypeId must be positive");
        }
    }
}
