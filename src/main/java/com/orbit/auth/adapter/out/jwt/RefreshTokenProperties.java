package com.orbit.auth.adapter.out.jwt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 자체 refresh token의 미사용 수명. 마지막 발급부터 이 기간 동안 쓰지 않으면 그 기기 세션이 만료된다. */
@ConfigurationProperties(prefix = "app.auth.refresh-token")
public record RefreshTokenProperties(Duration idleLifetime) {

    public RefreshTokenProperties {
        if (idleLifetime == null || idleLifetime.isZero() || idleLifetime.isNegative()) {
            throw new IllegalArgumentException("app.auth.refresh-token.idle-lifetime must be positive");
        }
    }
}
