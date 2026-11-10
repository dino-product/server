package com.orbit.auth.application.port.in.command.dto;

import java.time.Instant;

/** 재발급한 Access Token과 다음 refresh token. 문자열 표현에 토큰을 남기지 않는다. */
public record RefreshedTokensInfo(
        String accessToken, Instant accessTokenExpiresAt, String refreshToken, Instant refreshTokenExpiresAt) {

    @Override
    public String toString() {
        return "RefreshedTokensInfo[accessTokenExpiresAt=" + accessTokenExpiresAt + ", refreshTokenExpiresAt="
                + refreshTokenExpiresAt + "]";
    }
}
