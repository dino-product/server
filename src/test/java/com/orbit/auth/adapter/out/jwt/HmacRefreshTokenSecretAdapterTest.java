package com.orbit.auth.adapter.out.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.auth.domain.RefreshSessionId;

@DisplayName("refresh token 비밀값 생성·파생")
class HmacRefreshTokenSecretAdapterTest {

    private static final String JWT_SECRET = "test-only-secret-key-with-at-least-32-bytes";
    private static final RefreshSessionId SESSION = new RefreshSessionId("7f1c2b3a-4d5e-4f60-8a9b-0c1d2e3f4a5b");
    private static final RefreshSessionId OTHER_SESSION = new RefreshSessionId("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
    private static final Duration IDLE = Duration.ofDays(30);

    private final HmacRefreshTokenSecretAdapter adapter = adapter(JWT_SECRET);

    @Test
    @DisplayName("첫 비밀값은 32바이트 base64url(43자)이고 매번 다르다")
    void createsRandomSecrets() {
        String first = adapter.newSecret();
        String second = adapter.newSecret();

        assertThat(first).matches("[A-Za-z0-9_-]{43}");
        assertThat(second).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(first);
    }

    @Test
    @DisplayName("같은 세션·비밀값의 다음 비밀값은 언제나 같고 제출된 값과 다르다")
    void derivesSameNextSecret() {
        String secret = adapter.newSecret();

        String next = adapter.next(SESSION, secret);

        assertThat(next).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(secret);
        assertThat(adapter.next(SESSION, secret)).isEqualTo(next);
        assertThat(adapter(JWT_SECRET).next(SESSION, secret)).isEqualTo(next);
    }

    @Test
    @DisplayName("세션·비밀값·서버 비밀키가 다르면 다음 비밀값도 다르다")
    void derivesDifferentNextSecretForDifferentInput() {
        String secret = adapter.newSecret();
        String next = adapter.next(SESSION, secret);

        assertThat(adapter.next(OTHER_SESSION, secret)).isNotEqualTo(next);
        assertThat(adapter.next(SESSION, adapter.newSecret())).isNotEqualTo(next);
        assertThat(adapter("another-test-secret-key-with-32-bytes!!").next(SESSION, secret))
                .isNotEqualTo(next);
    }

    @Test
    @DisplayName("설정한 미사용 수명을 돌려준다")
    void exposesIdleLifetime() {
        assertThat(adapter.idleLifetime()).isEqualTo(IDLE);
    }

    @Test
    @DisplayName("미사용 수명이 0이거나 없으면 설정을 거부한다")
    void rejectsNonPositiveIdleLifetime() {
        assertThatThrownBy(() -> new RefreshTokenProperties(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.auth.refresh-token.idle-lifetime must be positive");
        assertThatThrownBy(() -> new RefreshTokenProperties(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.auth.refresh-token.idle-lifetime must be positive");
    }

    private static HmacRefreshTokenSecretAdapter adapter(String jwtSecret) {
        return new HmacRefreshTokenSecretAdapter(
                new JwtProperties(jwtSecret, "orbit-test", Duration.ofHours(1)), new RefreshTokenProperties(IDLE));
    }
}
