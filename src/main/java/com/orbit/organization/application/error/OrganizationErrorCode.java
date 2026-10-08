package com.orbit.organization.application.error;

import org.springframework.http.HttpStatus;

import com.orbit.shared.error.BaseCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** organization 모듈 전용 오류. 코드 문자열은 HTTP 상태와 독립이며 중복·재사용하지 않는다. */
@Getter
@RequiredArgsConstructor
public enum OrganizationErrorCode implements BaseCode {
    INVALID_ORGANIZATION_INPUT(HttpStatus.BAD_REQUEST, "ORGANIZATION-001", "발주사 정보가 올바르지 않습니다."),
    NOT_ORGANIZATION_MEMBER(HttpStatus.FORBIDDEN, "ORGANIZATION-002", "이 발주사의 소속이 아닙니다."),
    OWNER_ONLY(HttpStatus.FORBIDDEN, "ORGANIZATION-003", "총관리자만 할 수 있습니다."),
    MEMBERSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "ORGANIZATION-004", "직원 소속을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
