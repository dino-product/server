package com.orbit.auth.application.port.in.command.dto;

/** iOS 앱의 Apple 로그인 증명. id_token은 로그인 증명, authorization code는 토큰 철회용 refresh token을 받는 데 쓴다. */
public record LoginWithAppleCommand(String idToken, String authorizationCode) {

    @Override
    public String toString() {
        return "LoginWithAppleCommand[idToken=***, authorizationCode=***]";
    }
}
