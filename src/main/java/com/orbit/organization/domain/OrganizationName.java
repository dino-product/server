package com.orbit.organization.domain;

/** 발주사명. 길이는 화면에 보이는 문자 수에 가깝도록 코드 포인트로 센다. */
public record OrganizationName(String value) {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 30;

    public OrganizationName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("organizationName must not be blank");
        }
        int length = value.codePointCount(0, value.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new IllegalArgumentException("organizationName must be 2 to 30 characters");
        }
    }
}
