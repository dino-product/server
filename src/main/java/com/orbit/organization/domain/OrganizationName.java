package com.orbit.organization.domain;

public record OrganizationName(String value) {
    public OrganizationName {
        if (value == null) {
            throw new OrganizationRuleViolation("organization name must not be null");
        }
        value = value.strip();
        int length = value.codePointCount(0, value.length());
        if (length < 2 || length > 30) {
            throw new OrganizationRuleViolation("organization name must contain 2 to 30 characters");
        }
    }
}
