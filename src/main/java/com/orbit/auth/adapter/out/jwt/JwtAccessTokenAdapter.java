package com.orbit.auth.adapter.out.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.IssuedAccessToken;
import com.orbit.auth.domain.AccessToken;
import com.orbit.auth.domain.AccountId;

import lombok.extern.slf4j.Slf4j;

/**
 * HS256으로 서명한 자체 Access JWT를 발급·검증한다. 발급자·서명키가 같은 서버이므로 시계 오차를 허용하지 않고, token_use 클레임으로 다른 종류의
 * 토큰이 Access Token 자리에 쓰이지 않게 한다.
 */
@Slf4j
@Component
class JwtAccessTokenAdapter implements AccessTokenPort {

    static final String TOKEN_USE_CLAIM = "token_use";
    static final String ACCESS_TOKEN_USE = "access";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final JwtProperties properties;
    private final Clock clock;

    JwtAccessTokenAdapter(JwtProperties properties, Clock clock) {
        SecretKey secretKey = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        this.decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
        timestampValidator.setClock(clock);
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                new JwtIssuerValidator(properties.issuer()),
                new JwtClaimValidator<String>(TOKEN_USE_CLAIM, ACCESS_TOKEN_USE::equals)));
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public IssuedAccessToken issue(AccountId accountId) {
        Instant issuedAt = clock.instant();
        AccessToken token = new AccessToken(
                UUID.randomUUID().toString(), accountId, issuedAt, issuedAt.plus(properties.accessTokenTtl()));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(Long.toString(accountId.value()))
                .id(token.tokenId())
                .issuedAt(token.issuedAt())
                .expiresAt(token.expiresAt())
                .claim(TOKEN_USE_CLAIM, ACCESS_TOKEN_USE)
                .build();
        String value = encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedAccessToken(value, token);
    }

    @Override
    public Optional<AccessToken> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(value);
        } catch (JwtException exception) {
            log.debug("Access token rejected: {}", exception.getMessage());
            return Optional.empty();
        }
        // Spring 시간 검증기는 만료 정각을 유효로 보므로 RFC 7519대로 정각부터 거부한다.
        if (jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(clock.instant())) {
            log.debug("Access token rejected: expired");
            return Optional.empty();
        }
        try {
            return Optional.of(new AccessToken(
                    jwt.getId(),
                    new AccountId(Long.parseLong(jwt.getSubject())),
                    jwt.getIssuedAt(),
                    jwt.getExpiresAt()));
        } catch (IllegalArgumentException | NullPointerException exception) {
            log.debug("Access token rejected: invalid claims ({})", exception.getMessage());
            return Optional.empty();
        }
    }
}
