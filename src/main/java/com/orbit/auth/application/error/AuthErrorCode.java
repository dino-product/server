package com.orbit.auth.application.error;

import org.springframework.http.HttpStatus;

import com.orbit.shared.error.BaseCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH-001", "사용자를 찾을 수 없습니다."),
    INVALID_ID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-002", "소셜 로그인 인증 정보가 유효하지 않습니다."),
    INVALID_NONCE(HttpStatus.UNAUTHORIZED, "AUTH-003", "로그인 nonce가 유효하지 않거나 만료되었습니다."),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH-004", "계정을 찾을 수 없습니다."),
    INVALID_APPLE_AUTHORIZATION_CODE(
            HttpStatus.UNAUTHORIZED, "AUTH-005", "Apple 인증 코드가 유효하지 않거나 만료되었습니다. 다시 로그인해 주세요."),
    APPLE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AUTH-006", "Apple 인증 서버와 통신하지 못했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
