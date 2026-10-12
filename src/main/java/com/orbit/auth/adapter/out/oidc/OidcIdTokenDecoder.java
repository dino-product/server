package com.orbit.auth.adapter.out.oidc;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import lombok.extern.slf4j.Slf4j;

/**
 * OIDC 제공자의 id_token을 JWKS의 RS256 서명, 발급자, 허용 대상, 만료 시각으로 검증하는 공통 골격. 제공자별 어댑터는 JWKS·발급자·허용 대상만 넘기고 로그인에
 * 필요한 클레임 선택과 존재 요구는 직접 맡는다.
 */
@Slf4j
public final class OidcIdTokenDecoder {

    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private final String provider;
    private final NimbusJwtDecoder decoder;

    public OidcIdTokenDecoder(
            String provider,
            JWKSource<SecurityContext> jwkSource,
            String issuer,
            List<String> allowedAudiences,
            Clock clock) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        // 클레임 검증은 아래 Spring 검증기가 맡으므로 Nimbus 기본 검증기는 비운다.
        processor.setJWTClaimsSetVerifier(null);
        this.provider = provider;
        this.decoder = new NimbusJwtDecoder(processor);
        this.decoder.setJwtValidator(claimValidators(issuer, List.copyOf(allowedAudiences), clock));
    }

    /** 제공자 JWKS를 캐시·재시도와 함께 조회한다. 실제 조회는 첫 검증 시점에 일어나며 기동 시 네트워크에 접근하지 않는다. */
    public static JWKSource<SecurityContext> remoteJwkSource(String jwkSetUri) throws MalformedURLException {
        return JWKSourceBuilder.create(URI.create(jwkSetUri).toURL())
                .retrying(true)
                .build();
    }

    /** 서명·발급자·대상·만료가 모두 유효하면 디코딩한 토큰을, 하나라도 어긋나거나 형식이 아니면 빈 값을 반환한다. */
    public Optional<Jwt> decode(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(decoder.decode(idToken));
        } catch (JwtException exception) {
            log.debug("{} id_token rejected: {}", provider, exception.getMessage());
            return Optional.empty();
        }
    }

    /** 공백이 아닌 문자열 클레임만 돌려준다. 로그인에 필요한 클레임이 비었으면 제공자 어댑터가 토큰을 거부하는 데 쓴다. */
    public static Optional<String> nonBlankClaim(Jwt jwt, String claim) {
        String value = jwt.getClaimAsString(claim);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static OAuth2TokenValidator<Jwt> claimValidators(
            String issuer, List<String> allowedAudiences, Clock clock) {
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(CLOCK_SKEW);
        timestampValidator.setClock(clock);
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<Collection<String>>(
                JwtClaimNames.AUD,
                audience -> audience != null && audience.stream().anyMatch(allowedAudiences::contains));
        // Spring 시간 검증기는 exp가 없으면 만료를 검사하지 않으므로 exp 존재를 따로 요구한다.
        OAuth2TokenValidator<Jwt> expiryPresentValidator =
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull);
        return new DelegatingOAuth2TokenValidator<>(
                expiryPresentValidator, timestampValidator, new JwtIssuerValidator(issuer), audienceValidator);
    }
}
