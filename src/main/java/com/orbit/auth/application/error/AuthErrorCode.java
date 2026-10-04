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
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH-004", "계정을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
