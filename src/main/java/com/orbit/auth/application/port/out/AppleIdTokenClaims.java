package com.orbit.auth.application.port.out;

/**
 * 서명·발급자·대상·만료를 통과한 Apple id_token에서 로그인에 필요한 클레임만 고른 값. nonce는 앱이 넣은 해시 그대로이고, {@code clientId}는 허용 목록에
 * 있는 {@code aud}(토큰을 받은 클라이언트)로 authorization code 교환에 같은 값을 쓴다.
 */
public record AppleIdTokenClaims(String subject, String nonce, String clientId) {}
