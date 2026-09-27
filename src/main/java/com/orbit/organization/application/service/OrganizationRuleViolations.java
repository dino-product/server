package com.orbit.organization.application.service;

import java.util.function.Supplier;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.domain.OrganizationRuleViolation;
import com.orbit.shared.error.BusinessException;

/** 도메인 생성·변경 호출만 감싸며 포트, 시계, 변환 로직의 실패는 입력 오류로 바꾸지 않는다. */
final class OrganizationRuleViolations {
    private OrganizationRuleViolations() {}

    static <T> T call(Supplier<T> domainCall) {
        try {
            return domainCall.get();
        } catch (OrganizationRuleViolation violation) {
            throw new BusinessException(OrganizationErrorCode.INVALID_ORGANIZATION_INPUT, violation);
        }
    }
}
