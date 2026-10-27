package com.orbit.auth.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

public record LogoutRequest(
        @Schema(
                description = "이 기기의 refresh token. Access Token이 만료됐어도 이 값이 맞으면 이 기기의 로그인을 끝냅니다",
                example = "7f1c2b3a-4d5e-4f60-8a9b-0c1d2e3f4a5b.q3Jx0Yb9kVwH2tN7sLmA4cDeFgHiJkLmNoPqRsTuVwX")
        String refreshToken) {

    /** 요청을 로그에 남겨도 토큰이 찍히지 않게 한다. */
    @Override
    public String toString() {
        return "LogoutRequest[refreshToken=***]";
    }
}
