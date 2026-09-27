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
    COMPANY_CODE_CONFLICT(HttpStatus.CONFLICT, "ORGANIZATION-004", "회사 코드가 충돌했습니다. 다시 시도해 주세요."),
    PERSONNEL_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "ORGANIZATION-005", "유형을 찾을 수 없습니다."),
    DUPLICATE_PERSONNEL_TYPE_NAME(HttpStatus.CONFLICT, "ORGANIZATION-006", "이미 존재하는 유형 이름입니다."),
    PERSONNEL_TYPE_IN_USE(HttpStatus.CONFLICT, "ORGANIZATION-007", "사용 중인 유형은 삭제할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
