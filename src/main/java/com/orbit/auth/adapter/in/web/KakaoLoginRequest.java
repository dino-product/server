package com.orbit.auth.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

public record KakaoLoginRequest(
        @Schema(
                description = "카카오 SDK가 돌려준 OIDC id_token",
                example = "eyJraWQiOiI5ZjI1MmRhZGQ1ZjIzM2Y5M2QyZmE1MjhkMTJmZWEiLCJhbGciOiJSUzI1NiJ9...")
        @NotBlank(message = "필수입니다.")
        String idToken) {}
