package com.orbit.auth.adapter.in.web;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.orbit.support.AppleAuthStub;
import com.orbit.support.IntegrationTestSupport;
import com.orbit.support.KakaoJwksStub;

/**
 * 인증 HTTP API 통합 테스트의 공용 준비물. 카카오 JWKS·토큰과 Apple JWKS·토큰·철회 엔드포인트를 로컬 스텁으로 바꾸고 검증기·HTTP 호출·Redis·PostgreSQL은 실제
 * 경로로 지난다. 실제 카카오·Apple E2E는 아니다.
 *
 * <p>스텁 주소가 Spring 컨텍스트 설정에 들어가므로 스텁은 테스트 JVM에서 한 번만 띄우고 JVM이 끝날 때까지 둔다. 모든 인증 API 테스트가 같은 스텁 주소를 쓰므로 Spring
 * 컨텍스트도 함께 쓴다.
 */
abstract class AuthApiTestSupport extends IntegrationTestSupport {

    protected static final KakaoJwksStub KAKAO = KakaoJwksStub.start();
    protected static final AppleAuthStub APPLE = AppleAuthStub.start();

    @DynamicPropertySource
    static void stubEndpoints(DynamicPropertyRegistry registry) {
        registry.add("app.auth.kakao.jwk-set-uri", KAKAO::jwkSetUri);
        registry.add("app.auth.kakao.web.token-uri", KAKAO::tokenUri);
        registry.add("app.auth.apple.jwk-set-uri", APPLE::jwkSetUri);
        registry.add("app.auth.apple.token-api.token-uri", APPLE::tokenUri);
        registry.add("app.auth.apple.token-api.revoke-uri", APPLE::revokeUri);
        registry.add("app.auth.apple.client-secret.private-key", APPLE::clientSecretPrivateKeyPem);
    }
}
