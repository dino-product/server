package com.orbit.auth.adapter.in.web;

import java.time.Instant;

import com.orbit.auth.application.port.in.command.dto.LoginNonceInfo;

import io.swagger.v3.oas.annotations.media.Schema;

public record AppleLoginNonceResponse(
        @Schema(
                description = "raw nonce. 앱은 이 값의 UTF-8 SHA-256을 소문자 hex로 바꿔 Apple 로그인 요청의 nonce에 넣습니다",
                example = "Zm9vYmFyYmF6cXV4Zm9vYmFyYmF6cXV4Zm9vYmFyYmF6")
        String nonce,

        @Schema(description = "nonce 만료 시각(UTC)", example = "2026-10-04T01:05:00Z")
        Instant expiresAt) {

    static AppleLoginNonceResponse from(LoginNonceInfo info) {
        return new AppleLoginNonceResponse(info.nonce(), info.expiresAt());
    }
}
