package com.orbit.auth.application.port.out;

public interface ExchangeAppleAuthorizationCodePort {

    /**
     * Apple 토큰 엔드포인트에서 authorization code를 교환하고, 응답 id_token의 서명·발급자·대상·만료를 확인해 sub와 refresh token을 돌려준다.
     * {@code redirectUri}는 웹 흐름에서만 넘기고 앱이 받은 code는 {@code null}이다.
     *
     * @throws AppleTokenApiException Apple이 code를 거절하면 REJECTED, 통신·설정·응답 오류면 UNAVAILABLE
     */
    AppleCodeExchange exchange(String clientId, String authorizationCode, String redirectUri);
}
