package com.orbit.auth.application.port.out;

/**
 * 저장한 Apple refresh token을 발급받은 클라이언트로 철회한다. 계정 삭제 유즈케이스가 {@link AppleRefreshTokenRepository#listByAccount}로 읽은
 * 토큰마다 호출하며, Apple 정책상 계정 삭제를 제공하는 앱은 Sign in with Apple 토큰을 철회해야 한다.
 */
public interface RevokeAppleTokenPort {

    /** @throws AppleTokenApiException Apple이 요청을 거절하면 REJECTED, 통신·설정·응답 오류면 UNAVAILABLE */
    void revoke(AppleRefreshToken token);
}
