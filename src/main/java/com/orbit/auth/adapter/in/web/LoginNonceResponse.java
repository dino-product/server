package com.orbit.auth.adapter.in.web;

import java.time.Instant;

import com.orbit.auth.application.port.in.command.dto.LoginNonceInfo;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginNonceResponse(
        @Schema(description = "카카오 SDK 로그인에 그대로 넘길 nonce", example = "Zm9vYmFyYmF6cXV4")
        String nonce,

        @Schema(description = "nonce 만료 시각(UTC)", example = "2026-09-27T01:05:00Z")
        Instant expiresAt) {

    static LoginNonceResponse from(LoginNonceInfo info) {
        return new LoginNonceResponse(info.nonce(), info.expiresAt());
    }
}
