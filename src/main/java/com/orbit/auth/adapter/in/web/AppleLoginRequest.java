package com.orbit.auth.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

public record AppleLoginRequest(
        @Schema(
                description =
                        "iOS ASAuthorization이 돌려준 OIDC id_token. nonce 클레임에는 발급받은 raw nonce의 SHA-256 hex가 실려 있어야 합니다",
                example = "eyJraWQiOiJXNldjT0tCIiwiYWxnIjoiUlMyNTYifQ...")
        @NotBlank(message = "필수입니다.")
        String idToken,

        @Schema(
                description = "같은 로그인에서 받은 일회성 authorization code(5분 유효). 서버가 교환해 탈퇴 시 토큰 철회에 쓸 refresh token을 보관합니다",
                example = "c6a9f2e1b0d34f6c8e7a.0.rrzt.Zm9vYmFy")
        @NotBlank(message = "필수입니다.")
        String authorizationCode) {}
