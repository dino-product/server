package com.orbit.auth.adapter.in.web;

import java.time.Instant;

import com.orbit.auth.adapter.in.web.security.AuthenticatedAccount;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthenticatedAccountResponse(
        @Schema(description = "계정 식별자", example = "1") Long accountId,

        @Schema(description = "현재 Access Token 만료 시각(UTC)", example = "2026-09-27T02:00:00Z")
        Instant accessTokenExpiresAt) {

    static AuthenticatedAccountResponse from(AuthenticatedAccount account) {
        return new AuthenticatedAccountResponse(account.accountId(), account.tokenExpiresAt());
    }
}
