package com.orbit.auth.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.auth.domain.AccountId;
import com.orbit.support.RedisAdapterTestSupport;

@DisplayName("Redis 계정 폐쇄 표시 저장소")
class RedisClosedAccountAdapterTest extends RedisAdapterTestSupport {

    private RedisClosedAccountAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisClosedAccountAdapter(redisTemplate);
    }

    @Test
    @DisplayName("닫힌 계정은 보관 기간 동안 닫힌 상태다")
    void remembersClosedAccount() {
        AccountId accountId = newAccountId();

        adapter.markClosed(accountId, Duration.ofMinutes(30));

        assertThat(adapter.isClosed(accountId)).isTrue();
        assertThat(adapter.isClosed(newAccountId())).isFalse();
    }

    @Test
    @DisplayName("보관 기간이 지나면 잊는다")
    void forgetsAfterRetention() {
        AccountId accountId = newAccountId();
        adapter.markClosed(accountId, Duration.ofSeconds(1));

        await().atMost(Duration.ofSeconds(5)).until(() -> !adapter.isClosed(accountId));
    }

    private static AccountId newAccountId() {
        return new AccountId(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
    }
}
