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
        Instant accessTokenExpiresAt) {

    static LoginResponse from(LoginInfo info) {
        return new LoginResponse(info.accountId(), info.registered(), info.accessToken(), info.accessTokenExpiresAt());
    }
}
