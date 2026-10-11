package com.orbit.auth.adapter.out.kakao;

import java.time.Clock;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.orbit.auth.adapter.out.oidc.OidcIdTokenDecoder;
import com.orbit.auth.application.port.out.KakaoIdTokenClaims;
import com.orbit.auth.application.port.out.VerifyKakaoIdTokenPort;

import lombok.extern.slf4j.Slf4j;

/**
 * 카카오 id_token을 JWKS 서명, 발급자, 허용 대상, 만료 시각으로 검증한다. nonce는 서버가 발급한 값과의 대조를 Application이 맡으므로 여기서는 존재만
 * 요구한다.
 */
@Slf4j
@Component
class KakaoIdTokenVerifier implements VerifyKakaoIdTokenPort {

    private static final String NONCE_CLAIM = "nonce";

    private final OidcIdTokenDecoder decoder;

    KakaoIdTokenVerifier(
            @Qualifier("kakaoJwkSource") JWKSource<SecurityContext> kakaoJwkSource,
            KakaoOidcProperties properties,
            Clock clock) {
        this.decoder = new OidcIdTokenDecoder(
                "Kakao", kakaoJwkSource, properties.issuer(), properties.allowedAudiences(), clock);
    }

    @Override
    public Optional<KakaoIdTokenClaims> verify(String idToken) {
        return decoder.decode(idToken).flatMap(jwt -> {
            Optional<String> subject = OidcIdTokenDecoder.nonBlankClaim(jwt, JwtClaimNames.SUB);
            Optional<String> nonce = OidcIdTokenDecoder.nonBlankClaim(jwt, NONCE_CLAIM);
            if (subject.isEmpty() || nonce.isEmpty()) {
                log.debug("Kakao id_token rejected: missing sub or nonce");
                return Optional.empty();
            }
            return Optional.of(new KakaoIdTokenClaims(subject.get(), nonce.get()));
        });
    }
}
