package com.orbit.auth.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.support.RedisAdapterTestSupport;

@DisplayName("Redis 로그인 nonce 저장소")
class RedisLoginNonceAdapterTest extends RedisAdapterTestSupport {

    private RedisLoginNonceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisLoginNonceAdapter(redisTemplate);
    }

    @Test
    @DisplayName("보관한 nonce는 한 번만 소비된다")
    void consumesStoredNonceOnce() {
        String nonce = UUID.randomUUID().toString();
        adapter.store(nonce, Duration.ofMinutes(5));

        assertThat(adapter.consume(nonce)).isTrue();
        assertThat(adapter.consume(nonce)).isFalse();
    }

    @Test
    @DisplayName("보관하지 않은 nonce는 소비할 수 없다")
    void rejectsUnknownNonce() {
        assertThat(adapter.consume(UUID.randomUUID().toString())).isFalse();
    }

    @Test
    @DisplayName("만료된 nonce는 소비할 수 없다")
    void expiresNonceAfterTtl() {
        String nonce = UUID.randomUUID().toString();
        adapter.store(nonce, Duration.ofSeconds(1));

        await().atMost(Duration.ofSeconds(5))
                .until(() -> !Boolean.TRUE.equals(redisTemplate.hasKey(RedisLoginNonceAdapter.KEY_PREFIX + nonce)));
        assertThat(adapter.consume(nonce)).isFalse();
    }
}
