package com.orbit.auth.application.port.in.command.dto;

import java.time.Instant;

/**
 * 로그인 결과. {@code registered}는 이번 로그인으로 계정이 처음 만들어졌는지를 뜻한다. 로그인마다 기기 세션을 새로 시작하므로 refresh token도 그 세션의
 * 첫 토큰이다. 문자열 표현에 토큰을 남기지 않는다.
 */
public record LoginInfo(
        Long accountId,
        boolean registered,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt) {

    @Override
    public String toString() {
        return "LoginInfo[accountId=" + accountId + ", registered=" + registered + ", accessTokenExpiresAt="
                + accessTokenExpiresAt + ", refreshTokenExpiresAt=" + refreshTokenExpiresAt + "]";
    }
}
