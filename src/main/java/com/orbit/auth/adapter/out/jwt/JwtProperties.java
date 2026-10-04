package com.orbit.auth.adapter.out.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 자체 Access JWT의 HMAC 비밀키·발급자·유효 기간. 비밀키는 HS256 최소 길이인 32바이트 이상이어야 한다. */
@ConfigurationProperties(prefix = "app.auth.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl) {

    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("app.auth.jwt.secret must be at least 32 bytes");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("app.auth.jwt.issuer must not be blank");
        }
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("app.auth.jwt.access-token-ttl must be positive");
        }
    }
}
