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
    INVALID_NONCE(HttpStatus.UNAUTHORIZED, "AUTH-003", "로그인 확인 값(nonce·state)이 유효하지 않거나 만료되었습니다."),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH-004", "계정을 찾을 수 없습니다."),
    INVALID_APPLE_AUTHORIZATION_CODE(
            HttpStatus.UNAUTHORIZED, "AUTH-005", "Apple 인증 코드가 유효하지 않거나 만료되었습니다. 다시 로그인해 주세요."),
    APPLE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AUTH-006", "Apple 인증 서버와 통신하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    INVALID_LOGIN_EXCHANGE_CODE(HttpStatus.UNAUTHORIZED, "AUTH-007", "로그인 교환 코드가 유효하지 않거나 만료되었습니다. 다시 로그인해 주세요."),
    /** 웹·Android 콜백에서 사용자가 카카오·Apple 로그인을 취소했을 때 복귀 주소의 error 값으로만 쓴다. */
    LOGIN_CANCELLED(HttpStatus.UNAUTHORIZED, "AUTH-008", "로그인이 취소되었습니다."),
    /** 형식 오류·없는 세션·만료·재사용(탈취 의심)·철회를 구분하지 않는다. */
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-009", "로그인 유지 정보가 유효하지 않거나 만료되었습니다. 다시 로그인해 주세요."),
    /** 웹 카카오 로그인 콜백에서 카카오 토큰 API·설정 오류일 때 복귀 주소의 error 값으로만 쓴다. 카카오가 인가 코드를 거절했으면 AUTH-002다. */
    KAKAO_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AUTH-010", "카카오 인증 서버와 통신하지 못했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
