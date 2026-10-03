package com.orbit.auth.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.support.RedisAdapterTestSupport;

@DisplayName("Redis 폐기 Access Token 저장소")
class RedisRevokedAccessTokenAdapterTest extends RedisAdapterTestSupport {

    private RedisRevokedAccessTokenAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisRevokedAccessTokenAdapter(redisTemplate);
    }

    @Test
    @DisplayName("폐기한 토큰은 남은 유효 시간 동안 폐기 상태다")
    void remembersRevokedToken() {
        String tokenId = UUID.randomUUID().toString();

        adapter.revoke(tokenId, Duration.ofMinutes(30));

        assertThat(adapter.isRevoked(tokenId)).isTrue();
        assertThat(adapter.isRevoked(UUID.randomUUID().toString())).isFalse();
    }

    @Test
    @DisplayName("남은 유효 시간이 지나면 잊는다")
    void forgetsAfterRemainingLifetime() {
        String tokenId = UUID.randomUUID().toString();
        adapter.revoke(tokenId, Duration.ofSeconds(1));

        await().atMost(Duration.ofSeconds(5)).until(() -> !adapter.isRevoked(tokenId));
    }

    @Test
    @DisplayName("이미 만료된 토큰은 기억하지 않는다")
    void ignoresExpiredToken() {
        String tokenId = UUID.randomUUID().toString();

        adapter.revoke(tokenId, Duration.ZERO);
        adapter.revoke(tokenId, Duration.ofSeconds(-1));

        assertThat(adapter.isRevoked(tokenId)).isFalse();
    }
}
