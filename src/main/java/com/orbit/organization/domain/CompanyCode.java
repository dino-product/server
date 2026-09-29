package com.orbit.organization.domain;

public final class CompanyCode {
    private final String value;

    public CompanyCode(String value) {
        this(value, false);
    }

    private CompanyCode(String value, boolean restored) {
        if (value == null) {
            throw new OrganizationRuleViolation("company code must not be null");
        }
        if (!restored && !value.matches("[0-9A-HJKMNP-TV-Z]{8}")) {
            throw new OrganizationRuleViolation("company code must be eight uppercase Crockford Base32 characters");
        }
        this.value = value;
    }

    /** 저장된 원문을 보존하며 현재 입력 정책을 다시 적용하지 않는다. */
    public static CompanyCode reconstitute(String value) {
        return new CompanyCode(value, true);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CompanyCode that && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "CompanyCode[value=" + value + "]";
    }
}
