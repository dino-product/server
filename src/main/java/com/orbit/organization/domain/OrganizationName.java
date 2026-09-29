package com.orbit.organization.domain;

public final class OrganizationName {
    private final String value;

    public OrganizationName(String value) {
        this(value, false);
    }

    private OrganizationName(String value, boolean restored) {
        if (value == null) {
            throw new OrganizationRuleViolation("organization name must not be null");
        }
        if (!restored) {
            value = value.strip();
            int length = value.codePointCount(0, value.length());
            if (length < 2 || length > 30) {
                throw new OrganizationRuleViolation("organization name must contain 2 to 30 characters");
            }
        }
        this.value = value;
    }

    /** 저장된 원문을 보존하며 현재 입력 정책을 다시 적용하지 않는다. */
    public static OrganizationName reconstitute(String value) {
        return new OrganizationName(value, true);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OrganizationName that && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "OrganizationName[value=" + value + "]";
    }
}
