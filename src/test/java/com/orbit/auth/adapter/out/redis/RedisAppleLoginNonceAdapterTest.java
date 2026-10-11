package com.orbit.auth.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.auth.domain.HashedNonce;
import com.orbit.support.RedisAdapterTestSupport;

@DisplayName("Redis Apple 로그인 nonce 저장소")
class RedisAppleLoginNonceAdapterTest extends RedisAdapterTestSupport {

    private RedisAppleLoginNonceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisAppleLoginNonceAdapter(redisTemplate);
    }

    @Test
    @DisplayName("보관한 해시 nonce는 한 번만 소비된다")
    void consumesStoredNonceOnce() {
        HashedNonce nonce = randomNonce();
        adapter.store(nonce, Duration.ofMinutes(5));

        assertThat(adapter.consume(nonce)).isTrue();
        assertThat(adapter.consume(nonce)).isFalse();
    }

    @Test
    @DisplayName("보관하지 않은 해시 nonce는 소비할 수 없다")
    void rejectsUnknownNonce() {
        assertThat(adapter.consume(randomNonce())).isFalse();
    }

    @Test
    @DisplayName("카카오 nonce 저장소에 같은 문자열이 있어도 Apple nonce로 소비할 수 없다")
    void doesNotShareKeysWithKakaoNonces() {
        HashedNonce nonce = randomNonce();
        new RedisLoginNonceAdapter(redisTemplate).store(nonce.value(), Duration.ofMinutes(5));

        assertThat(adapter.consume(nonce)).isFalse();
    }

    @Test
    @DisplayName("만료된 해시 nonce는 소비할 수 없다")
    void expiresNonceAfterTtl() {
        HashedNonce nonce = randomNonce();
        adapter.store(nonce, Duration.ofSeconds(1));

        await().atMost(Duration.ofSeconds(5))
                .until(() -> !Boolean.TRUE.equals(
                        redisTemplate.hasKey(RedisAppleLoginNonceAdapter.KEY_PREFIX + nonce.value())));
        assertThat(adapter.consume(nonce)).isFalse();
    }

    private static HashedNonce randomNonce() {
        return HashedNonce.fromRaw(UUID.randomUUID().toString());
    }
}
