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
import com.orbit.auth.domain.PkceChallenge;
import com.orbit.support.RedisAdapterTestSupport;

@DisplayName("Redis Apple 웹 로그인 state 저장소")
class RedisAppleWebLoginStateAdapterTest extends RedisAdapterTestSupport {

    private static final String BINDING = HashedNonce.fromRaw("browser-binding").value();

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
        adapter.save(state, BINDING, pending, Duration.ofMinutes(10));

        assertThat(adapter.consume(state, BINDING)).contains(pending);
        assertThat(adapter.consume(state, BINDING)).isEmpty();
    }

    @Test
    @DisplayName("보관하지 않은 state는 소비할 수 없다")
    void rejectsUnknownState() {
        assertThat(adapter.consume(UUID.randomUUID().toString(), BINDING)).isEmpty();
    }

    @Test
    @DisplayName("만료된 state는 소비할 수 없다")
    void expiresStateAfterTtl() {
        String state = UUID.randomUUID().toString();
        adapter.save(state, BINDING, pending("orbit://auth/apple"), Duration.ofSeconds(1));

        await().atMost(Duration.ofSeconds(5))
                .until(() ->
                        !Boolean.TRUE.equals(redisTemplate.hasKey(RedisAppleWebLoginStateAdapter.key(state, BINDING))));
        assertThat(adapter.consume(state, BINDING)).isEmpty();
    }

    @Test
    @DisplayName("다른 브라우저 연결 값으로는 찾지 못하고 원래 로그인의 state도 지우지 않는다")
    void keepsStateWhenBindingDiffers() {
        String state = UUID.randomUUID().toString();
        AppleWebLoginState pending = pending("orbit://auth/apple");
        adapter.save(state, BINDING, pending, Duration.ofMinutes(10));

        assertThat(adapter.consume(state, HashedNonce.fromRaw("attacker").value()))
                .isEmpty();
        assertThat(adapter.consume(state, BINDING)).contains(pending);
    }

    private static AppleWebLoginState pending(String returnUri) {
        return new AppleWebLoginState(
                HashedNonce.fromRaw(UUID.randomUUID().toString()),
                returnUri,
                new PkceChallenge("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"));
    }
}
