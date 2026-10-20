package com.orbit.auth.application.port.in.command.dto;

/** 클라이언트가 보관한 refresh token. 문자열 표현에 토큰을 남기지 않는다. */
public record RefreshTokensCommand(String refreshToken) {

    @Override
    public String toString() {
        return "RefreshTokensCommand[refreshToken=***]";
    }
}
