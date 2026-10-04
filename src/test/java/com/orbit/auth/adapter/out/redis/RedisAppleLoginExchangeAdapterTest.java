package com.orbit.auth.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.auth.application.port.out.PendingAppleLogin;
import com.orbit.auth.domain.AccountId;
import com.orbit.support.RedisAdapterTestSupport;

@DisplayName("Redis Apple 로그인 교환 코드 저장소")
class RedisAppleLoginExchangeAdapterTest extends RedisAdapterTestSupport {

    private RedisAppleLoginExchangeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisAppleLoginExchangeAdapter(redisTemplate);
    }

    @Test
    @DisplayName("보관한 교환 코드는 계정과 등록 여부를 담아 한 번만 소비된다")
    void consumesExchangeCodeOnce() {
        String code = UUID.randomUUID().toString();
        adapter.save(code, new PendingAppleLogin(new AccountId(7L), true), Duration.ofSeconds(60));

        assertThat(adapter.consume(code)).contains(new PendingAppleLogin(new AccountId(7L), true));
        assertThat(adapter.consume(code)).isEmpty();
    }

    @Test
    @DisplayName("보관하지 않은 교환 코드는 소비할 수 없고 TTL을 지킨다")
    void rejectsUnknownAndKeepsTtl() {
        String code = UUID.randomUUID().toString();
        adapter.save(code, new PendingAppleLogin(new AccountId(8L), false), Duration.ofSeconds(60));

        assertThat(adapter.consume(UUID.randomUUID().toString())).isEmpty();
        assertThat(redisTemplate.getExpire(RedisAppleLoginExchangeAdapter.KEY_PREFIX + code))
                .isBetween(1L, 60L);
    }
}
