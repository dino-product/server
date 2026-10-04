package com.orbit.auth.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.auth.application.port.out.AppleWebLoginState;
import com.orbit.auth.domain.HashedNonce;
import com.orbit.support.RedisAdapterTestSupport;

@DisplayName("Redis Apple 웹 로그인 state 저장소")
class RedisAppleWebLoginStateAdapterTest extends RedisAdapterTestSupport {

    private RedisAppleWebLoginStateAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisAppleWebLoginStateAdapter(redisTemplate);
    }

    @Test
    @DisplayName("보관한 state는 내용 그대로 한 번만 소비된다")
    void consumesStoredStateOnce() {
        String state = UUID.randomUUID().toString();
        AppleWebLoginState pending = pending("https://admin.example.com/login/apple?from=a|b");
        adapter.save(state, pending, Duration.ofMinutes(10));

        assertThat(adapter.consume(state)).contains(pending);
        assertThat(adapter.consume(state)).isEmpty();
    }

    @Test
    @DisplayName("보관하지 않은 state는 소비할 수 없다")
    void rejectsUnknownState() {
        assertThat(adapter.consume(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    @DisplayName("만료된 state는 소비할 수 없다")
    void expiresStateAfterTtl() {
        String state = UUID.randomUUID().toString();
        adapter.save(state, pending("orbit://auth/apple"), Duration.ofSeconds(1));

        await().atMost(Duration.ofSeconds(5))
                .until(() ->
                        !Boolean.TRUE.equals(redisTemplate.hasKey(RedisAppleWebLoginStateAdapter.KEY_PREFIX + state)));
        assertThat(adapter.consume(state)).isEmpty();
    }

    private static AppleWebLoginState pending(String returnUri) {
        return new AppleWebLoginState(
                HashedNonce.fromRaw(UUID.randomUUID().toString()),
                HashedNonce.fromRaw(UUID.randomUUID().toString()).value(),
                returnUri);
    }
}
