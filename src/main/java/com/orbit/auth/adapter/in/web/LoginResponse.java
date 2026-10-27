package com.orbit.auth.adapter.in.web;

import java.time.Instant;

import com.orbit.auth.application.port.in.command.dto.LoginInfo;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
        @Schema(description = "계정 식별자", example = "1") Long accountId,

        @Schema(description = "이번 로그인으로 계정이 처음 만들어졌는지", example = "true")
        boolean registered,

        @Schema(description = "Authorization: Bearer 헤더에 넣을 Access Token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "Access Token 만료 시각(UTC)", example = "2026-09-27T02:00:00Z")
        Instant accessTokenExpiresAt,

        @Schema(
                description = "Access Token을 다시 받을 때 쓰는 refresh token. 이 기기의 보안 저장소에 보관하고, 재발급할 때마다 새 값으로 바꿉니다",
                example = "7f1c2b3a-4d5e-4f60-8a9b-0c1d2e3f4a5b.q3Jx0Yb9kVwH2tN7sLmA4cDeFgHiJkLmNoPqRsTuVwX")
        String refreshToken,

        @Schema(description = "refresh token 만료 시각(UTC). 재발급할 때마다 그 시각부터 다시 늘어납니다", example = "2026-10-27T01:00:00Z")
        Instant refreshTokenExpiresAt) {

    static LoginResponse from(LoginInfo info) {
        return new LoginResponse(
                info.accountId(),
                info.registered(),
                info.accessToken(),
                info.accessTokenExpiresAt(),
                info.refreshToken(),
                info.refreshTokenExpiresAt());
    }

    /** 응답을 로그에 남겨도 토큰이 찍히지 않게 한다. */
    @Override
    public String toString() {
        return "LoginResponse[accountId=" + accountId + ", registered=" + registered + ", accessTokenExpiresAt="
                + accessTokenExpiresAt + ", refreshTokenExpiresAt=" + refreshTokenExpiresAt + "]";
    }
}
