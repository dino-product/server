package com.orbit.auth.adapter.out.kakao;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
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
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private final NimbusJwtDecoder decoder;

    KakaoIdTokenVerifier(JWKSource<SecurityContext> kakaoJwkSource, KakaoOidcProperties properties, Clock clock) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, kakaoJwkSource));
        // 클레임 검증은 아래 Spring 검증기가 맡으므로 Nimbus 기본 검증기는 비운다.
        processor.setJWTClaimsSetVerifier(null);
        this.decoder = new NimbusJwtDecoder(processor);
        this.decoder.setJwtValidator(claimValidators(properties, clock));
    }

    @Override
    public Optional<KakaoIdTokenClaims> verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            return Optional.empty();
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException exception) {
            log.debug("Kakao id_token rejected: {}", exception.getMessage());
            return Optional.empty();
        }
        String subject = jwt.getSubject();
        String nonce = jwt.getClaimAsString(NONCE_CLAIM);
        if (subject == null || subject.isBlank() || nonce == null || nonce.isBlank()) {
            log.debug("Kakao id_token rejected: missing sub or nonce");
            return Optional.empty();
        }
        return Optional.of(new KakaoIdTokenClaims(subject, nonce));
    }

    private static OAuth2TokenValidator<Jwt> claimValidators(KakaoOidcProperties properties, Clock clock) {
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(CLOCK_SKEW);
        timestampValidator.setClock(clock);
        List<String> allowedAudiences = properties.allowedAudiences();
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<Collection<String>>(
                JwtClaimNames.AUD,
                audience -> audience != null && audience.stream().anyMatch(allowedAudiences::contains));
        return new DelegatingOAuth2TokenValidator<>(
                timestampValidator, new JwtIssuerValidator(properties.issuer()), audienceValidator);
    }
}
