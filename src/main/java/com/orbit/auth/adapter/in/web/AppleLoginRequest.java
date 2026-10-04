package com.orbit.auth.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

public record AppleLoginRequest(
        @Schema(
                description =
                        "iOS ASAuthorization이 돌려준 OIDC id_token. nonce 클레임에는 발급받은 raw nonce의 SHA-256 hex가 실려 있어야 합니다",
                example = "eyJraWQiOiJXNldjT0tCIiwiYWxnIjoiUlMyNTYifQ...")
        @NotBlank(message = "필수입니다.")
        String idToken) {}
