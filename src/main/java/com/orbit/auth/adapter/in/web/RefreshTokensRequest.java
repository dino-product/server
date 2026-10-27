package com.orbit.auth.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

public record RefreshTokensRequest(
        @Schema(
                description = "로그인·재발급 응답으로 받은 refresh token",
                example = "7f1c2b3a-4d5e-4f60-8a9b-0c1d2e3f4a5b.q3Jx0Yb9kVwH2tN7sLmA4cDeFgHiJkLmNoPqRsTuVwX")
        @NotBlank(message = "필수입니다.")
        String refreshToken) {

    /** 요청을 로그에 남겨도 토큰이 찍히지 않게 한다. */
    @Override
    public String toString() {
        return "RefreshTokensRequest[refreshToken=***]";
    }
}
