package com.orbit.auth.application.port.out;

/** 계정에 저장된 Apple refresh token. 계정 삭제 시 발급받은 클라이언트로 철회한다. 문자열 표현에서 토큰을 가린다. */
public record AppleRefreshToken(String clientId, String refreshToken) {

    @Override
    public String toString() {
        return "AppleRefreshToken[clientId=" + clientId + ", refreshToken=***]";
    }
}
