package com.orbit.organization.domain;

public record StaffTypeId(Long value) {
    public StaffTypeId {
        if (value == null || value <= 0) {
            throw new OrganizationRuleViolation("StaffTypeId must be positive");
        }
    }
}
