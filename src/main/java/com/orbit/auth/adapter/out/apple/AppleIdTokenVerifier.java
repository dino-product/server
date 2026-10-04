package com.orbit.auth.adapter.out.apple;

import java.time.Clock;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.orbit.auth.adapter.out.oidc.OidcIdTokenDecoder;
import com.orbit.auth.application.port.out.AppleIdTokenClaims;
import com.orbit.auth.application.port.out.VerifyAppleIdTokenPort;

import lombok.extern.slf4j.Slf4j;

/**
 * Apple id_token을 JWKS 서명, 발급자, 허용 대상, 만료 시각으로 검증한다. nonce 클레임은 앱이 넣은 해시이며 서버 발급 값과의 대조는 Application이 맡으므로
 * 여기서는 존재만 요구한다.
 */
@Slf4j
@Component
class AppleIdTokenVerifier implements VerifyAppleIdTokenPort {

    private static final String NONCE_CLAIM = "nonce";

    private final OidcIdTokenDecoder decoder;

    AppleIdTokenVerifier(
            @Qualifier("appleJwkSource") JWKSource<SecurityContext> appleJwkSource,
            AppleOidcProperties properties,
            Clock clock) {
        this.decoder = new OidcIdTokenDecoder(
                "Apple", appleJwkSource, properties.issuer(), properties.allowedAudiences(), clock);
    }

    @Override
    public Optional<AppleIdTokenClaims> verify(String idToken) {
        return decoder.decode(idToken).flatMap(jwt -> {
            Optional<String> subject = OidcIdTokenDecoder.nonBlankClaim(jwt, JwtClaimNames.SUB);
            Optional<String> nonce = OidcIdTokenDecoder.nonBlankClaim(jwt, NONCE_CLAIM);
            if (subject.isEmpty() || nonce.isEmpty()) {
                log.debug("Apple id_token rejected: missing sub or nonce");
                return Optional.empty();
            }
            return Optional.of(new AppleIdTokenClaims(subject.get(), nonce.get()));
        });
    }
}
