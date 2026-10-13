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
    MEMBERSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "ORGANIZATION-004", "직원 소속을 찾을 수 없습니다."),
    LAST_OWNER(HttpStatus.CONFLICT, "ORGANIZATION-005", "마지막 총관리자는 해제할 수 없습니다."),
    INACTIVE_MEMBERSHIP(HttpStatus.CONFLICT, "ORGANIZATION-006", "비활성 소속은 총관리자로 지정할 수 없습니다."),
    INVALID_CATEGORY_INPUT(HttpStatus.BAD_REQUEST, "ORGANIZATION-007", "유형 정보가 올바르지 않습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "ORGANIZATION-008", "유형을 찾을 수 없습니다."),
    DUPLICATE_CATEGORY_NAME(HttpStatus.CONFLICT, "ORGANIZATION-009", "같은 이름의 유형이 이미 있습니다."),
    ORGANIZATION_BUSY(HttpStatus.CONFLICT, "ORGANIZATION-010", "같은 발주사의 정보가 다른 요청으로 변경 중입니다. 잠시 후 다시 시도해 주세요."),
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "ORGANIZATION-011", "회사 코드에 해당하는 회사가 없습니다."),
    RETIRED_COMPANY_CODE(HttpStatus.NOT_FOUND, "ORGANIZATION-012", "변경 전의 이전 회사 코드입니다. 현재 회사 코드를 다시 확인해 주세요."),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "ORGANIZATION-013", "이미 참여 중인 회사입니다."),
    PENDING_REQUEST_EXISTS(HttpStatus.CONFLICT, "ORGANIZATION-014", "이 회사에 승인 대기 중인 참여 요청이 있습니다."),
    DEACTIVATED_MEMBER(HttpStatus.CONFLICT, "ORGANIZATION-015", "비활성화된 소속이 있는 회사입니다. 총관리자에게 재활성화를 요청해 주세요."),
    NOT_REQUESTER(HttpStatus.FORBIDDEN, "ORGANIZATION-016", "본인의 참여 요청이 아닙니다."),
    REQUEST_NOT_PENDING(HttpStatus.CONFLICT, "ORGANIZATION-017", "승인 대기 중인 참여 요청만 취소할 수 있습니다."),
    TOO_MANY_COMPANY_CODE_ATTEMPTS(
            HttpStatus.TOO_MANY_REQUESTS, "ORGANIZATION-018", "회사 코드 확인에 여러 번 실패했습니다. 잠시 후 다시 시도해 주세요."),
    INVALID_REJECT_REASON(HttpStatus.BAD_REQUEST, "ORGANIZATION-019", "거절 사유가 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
