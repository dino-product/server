package com.orbit.auth.adapter.out.apple;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Apple 토큰·철회 엔드포인트 주소와 호출 제한 시간. 테스트는 주소만 로컬 스텁으로 바꾼다. */
@ConfigurationProperties(prefix = "app.auth.apple.token-api")
public record AppleTokenApiProperties(String tokenUri, String revokeUri, Duration timeout) {

    public AppleTokenApiProperties {
        if (tokenUri == null || tokenUri.isBlank()) {
            throw new IllegalArgumentException("app.auth.apple.token-api.token-uri must not be blank");
        }
        if (revokeUri == null || revokeUri.isBlank()) {
            throw new IllegalArgumentException("app.auth.apple.token-api.revoke-uri must not be blank");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("app.auth.apple.token-api.timeout must be positive");
        }
    }
}
