package com.orbit.auth.application.port.in.command.dto;

/** 교환 코드와 로그인을 시작한 클라이언트의 PKCE code_verifier. 비밀값이 로그에 남지 않도록 문자열 표현에서 가린다. */
public record ExchangeAppleWebLoginCommand(String code, String codeVerifier) {

    @Override
    public String toString() {
        return "ExchangeAppleWebLoginCommand[code=***, codeVerifier=***]";
    }
}
