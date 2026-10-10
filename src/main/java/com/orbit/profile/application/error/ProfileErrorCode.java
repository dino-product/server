package com.orbit.profile.application.error;

import org.springframework.http.HttpStatus;

import com.orbit.shared.error.BaseCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** profile 모듈 전용 오류. 코드 문자열은 HTTP 상태와 독립이며 중복·재사용하지 않는다. */
@Getter
@RequiredArgsConstructor
public enum ProfileErrorCode implements BaseCode {
    INVALID_PROFILE_INPUT(HttpStatus.BAD_REQUEST, "PROFILE-001", "프로필 정보가 올바르지 않습니다."),
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "PROFILE-002", "프로필을 찾을 수 없습니다."),
    PROFILE_REQUIRED(HttpStatus.CONFLICT, "PROFILE-003", "프로필을 먼저 입력해야 합니다."),
    REQUIRED_TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "PROFILE-004", "필수 약관에 모두 동의해야 합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
