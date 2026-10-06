package com.orbit.auth.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

public record AppleLoginExchangeRequest(
        @Schema(
                description = "웹·Android Apple 로그인 콜백이 복귀 주소의 code 파라미터로 넘긴 일회성 교환 코드(60초 유효)",
                example = "Zm9vYmFyYmF6cXV4Zm9vYmFyYmF6cXV4Zm9vYmFyYmF6")
        @NotBlank(message = "필수입니다.")
        String code,

        @Schema(
                description = "로그인을 시작할 때 보낸 code_challenge의 원본 PKCE code_verifier(43~128자)",
                example = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        @NotBlank(message = "필수입니다.")
        String codeVerifier) {

    @Override
    public String toString() {
        return "AppleLoginExchangeRequest[code=***, codeVerifier=***]";
    }
}
