package com.orbit.organization.domain;

public record CompanyCode(String value) {
    // TODO: 회사 코드 길이·허용 문자·대소문자 구분·발급/변경 정책 합의 후 검증을 구체화한다.
    public CompanyCode {
        if (value == null || value.isBlank()) {
            throw new OrganizationRuleViolation("company code must not be blank");
        }
    }
}
