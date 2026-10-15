package com.orbit.auth.application.port.out;

/**
 * Apple authorization code 교환 결과. {@code subject}는 응답 id_token을 검증해 읽은 sub, {@code clientId}는 교환에 쓴 클라이언트이며
 * {@code refreshToken}은 계정 삭제 시 토큰 철회에 쓴다. 비밀값이 로그에 남지 않도록 문자열 표현에서 refresh token을 가린다.
 */
public record AppleCodeExchange(String subject, String clientId, String refreshToken) {

    @Override
    public String toString() {
        return "AppleCodeExchange[clientId=" + clientId + ", refreshToken=***]";
    }
}
