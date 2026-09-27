package com.orbit.organization.domain;

public record CompanyCode(String value) {
    public CompanyCode {
        if (value == null || !value.matches("[0-9A-HJKMNP-TV-Z]{8}")) {
            throw new OrganizationRuleViolation("company code must be eight uppercase Crockford Base32 characters");
        }
    }
}
