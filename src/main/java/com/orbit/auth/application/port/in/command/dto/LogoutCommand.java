package com.orbit.auth.application.port.in.command.dto;

import java.time.Instant;

/**
 * 로그아웃 대상. 앞의 네 값은 인증 필터가 확인한 현재 Access Token의 계정·식별자·만료 시각과 그 토큰을 발급한 기기 세션이며, 유효한 Access Token이 없으면
 * 모두 null이다(도입 전 토큰은 세션만 null). {@code refreshToken}은 클라이언트가 함께 보낸 이 기기의 refresh token이다. 문자열 표현에 토큰을 남기지
 * 않는다.
 */
public record LogoutCommand(
        Long accountId, String tokenId, Instant tokenExpiresAt, String sessionId, String refreshToken) {

    @Override
    public String toString() {
        return "LogoutCommand[accountId=" + accountId + ", tokenExpiresAt=" + tokenExpiresAt + "]";
    }
}
