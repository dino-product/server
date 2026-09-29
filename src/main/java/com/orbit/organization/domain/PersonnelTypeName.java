package com.orbit.organization.domain;

public record PersonnelTypeName(String value) {
    public PersonnelTypeName {
        if (value == null) {
            throw new OrganizationRuleViolation("type name must not be null");
        }
        value = value.strip();
        int length = value.codePointCount(0, value.length());
        if (length < 2 || length > 10) {
            throw new OrganizationRuleViolation("type name must contain 2 to 10 characters");
        }
    }
}
