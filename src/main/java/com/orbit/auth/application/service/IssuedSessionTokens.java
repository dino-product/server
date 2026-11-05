package com.orbit.auth.application.service;

import java.time.Instant;

/** 기기 세션 하나로 발급한 Access Token과 refresh token. 문자열 표현에 토큰을 남기지 않는다. */
record IssuedSessionTokens(
        String accessToken, Instant accessTokenExpiresAt, String refreshToken, Instant refreshTokenExpiresAt) {

    @Override
    public String toString() {
        return "IssuedSessionTokens[accessTokenExpiresAt=" + accessTokenExpiresAt + ", refreshTokenExpiresAt="
                + refreshTokenExpiresAt + "]";
    }
}
