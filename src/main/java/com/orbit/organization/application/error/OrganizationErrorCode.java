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
    NOT_ORGANIZATION_MEMBER(HttpStatus.FORBIDDEN, "ORGANIZATION-002", "발주사의 활성 소속이 아닙니다."),
    OWNER_ONLY(HttpStatus.FORBIDDEN, "ORGANIZATION-003", "총관리자만 할 수 있습니다."),
    // ORGANIZATION-004~006은 총관리자 지정·해제(HM-294)가 쓴다.
    INVALID_CATEGORY_INPUT(HttpStatus.BAD_REQUEST, "ORGANIZATION-007", "유형 정보가 올바르지 않습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "ORGANIZATION-008", "유형을 찾을 수 없습니다."),
    DUPLICATE_CATEGORY_NAME(HttpStatus.CONFLICT, "ORGANIZATION-009", "같은 이름의 유형이 이미 있습니다."),
    ORGANIZATION_BUSY(HttpStatus.CONFLICT, "ORGANIZATION-010", "같은 발주사의 정보가 다른 요청으로 변경 중입니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
