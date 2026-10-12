package com.orbit.organization.application.service;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.OrganizationName;
import com.orbit.shared.error.BusinessException;

/** 발주사 생성·수정이 함께 쓰는 입력 검증. 규칙은 도메인 값객체가 소유하고 여기서는 위반을 ORGANIZATION-001로 바꾼다. */
final class OrganizationInputs {

    private OrganizationInputs() {}

    static OrganizationName name(String value) {
        try {
            return new OrganizationName(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(OrganizationErrorCode.INVALID_ORGANIZATION_INPUT, exception);
        }
    }

    static Industry industry(Industry value) {
        if (value == null) {
            throw new BusinessException(OrganizationErrorCode.INVALID_ORGANIZATION_INPUT);
        }
        return value;
    }
}
