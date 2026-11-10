package com.orbit.auth.adapter.in.web;

import java.time.Instant;

import com.orbit.auth.application.port.in.command.dto.RefreshedTokensInfo;

import io.swagger.v3.oas.annotations.media.Schema;

public record RefreshedTokensResponse(
        @Schema(description = "Authorization: Bearer 헤더에 넣을 새 Access Token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "Access Token 만료 시각(UTC)", example = "2026-10-17T02:00:00Z")
        Instant accessTokenExpiresAt,

        @Schema(
                description = "다음 재발급에 쓸 refresh token. 보낸 refresh token은 더 쓸 수 없으니 이 값으로 바꿔 보관합니다",
                example = "7f1c2b3a-4d5e-4f60-8a9b-0c1d2e3f4a5b.Zp8sQw2rTy6uIo0pAs4dFg7hJk1lZx3cVb5nMq9wErT")
        String refreshToken,

        @Schema(description = "refresh token 만료 시각(UTC). 재발급한 시각부터 다시 센 값입니다", example = "2026-11-16T01:00:00Z")
        Instant refreshTokenExpiresAt) {

    static RefreshedTokensResponse from(RefreshedTokensInfo info) {
        return new RefreshedTokensResponse(
                info.accessToken(), info.accessTokenExpiresAt(), info.refreshToken(), info.refreshTokenExpiresAt());
    }

    /** 응답을 로그에 남겨도 토큰이 찍히지 않게 한다. */
    @Override
    public String toString() {
        return "RefreshedTokensResponse[accessTokenExpiresAt=" + accessTokenExpiresAt + ", refreshTokenExpiresAt="
                + refreshTokenExpiresAt + "]";
    }
}
