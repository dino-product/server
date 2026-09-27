package com.orbit.organization.application.error;

import org.springframework.http.HttpStatus;

import com.orbit.shared.error.BaseCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrganizationErrorCode implements BaseCode {
    INVALID_ORGANIZATION_INPUT(HttpStatus.BAD_REQUEST, "ORGANIZATION-001", "발주사 입력 값이 올바르지 않습니다."),
    NOT_ORGANIZATION_OWNER(HttpStatus.FORBIDDEN, "ORGANIZATION-002", "발주사 총관리자가 아닙니다."),
    COMPANY_CODE_EXHAUSTED(HttpStatus.INTERNAL_SERVER_ERROR, "ORGANIZATION-003", "회사 코드를 발급할 수 없습니다."),
    COMPANY_CODE_CONFLICT(HttpStatus.CONFLICT, "ORGANIZATION-004", "회사 코드가 충돌했습니다. 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
