package com.orbit.organization.domain;

/** 조직 도메인의 값·상태 불변식 위반. HTTP 오류 변환은 호출 경계의 책임이다. */
public final class OrganizationRuleViolation extends IllegalArgumentException {
    public OrganizationRuleViolation(String message) {
        super(message);
    }
}
