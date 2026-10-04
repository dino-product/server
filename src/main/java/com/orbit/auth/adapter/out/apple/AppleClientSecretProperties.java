package com.orbit.auth.adapter.out.apple;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Apple 토큰 API 호출에 쓰는 client_secret 서명 설정. 개인키는 Apple 개발자 콘솔에서 받은 {@code .p8}(PKCS#8 PEM)이며 처음 client_secret을 만들 때
 * 해석한다. 유효 기간은 Apple 상한(15777000초, 약 6개월)을 넘을 수 없다.
 */
@ConfigurationProperties(prefix = "app.auth.apple.client-secret")
public record AppleClientSecretProperties(String teamId, String keyId, String privateKey, Duration ttl) {

    static final Duration MAX_TTL = Duration.ofSeconds(15_777_000);

    public AppleClientSecretProperties {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("app.auth.apple.client-secret.team-id must not be blank");
        }
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("app.auth.apple.client-secret.key-id must not be blank");
        }
        if (privateKey == null || privateKey.isBlank()) {
            throw new IllegalArgumentException("app.auth.apple.client-secret.private-key must not be blank");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero() || ttl.compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException(
                    "app.auth.apple.client-secret.ttl must be positive and at most " + MAX_TTL);
        }
    }
}
