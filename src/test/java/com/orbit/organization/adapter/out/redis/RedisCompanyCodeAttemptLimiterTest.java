package com.orbit.organization.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.orbit.organization.domain.AccountId;
import com.orbit.support.RedisAdapterTestSupport;

@DisplayName("Redis 회사 코드 확인 실패 횟수 제한")
class RedisCompanyCodeAttemptLimiterTest extends RedisAdapterTestSupport {

    private static final AtomicLong ACCOUNT_IDS = new AtomicLong(1_000);

    private RedisCompanyCodeAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new RedisCompanyCodeAttemptLimiter(redisTemplate);
    }

    @Test
    @DisplayName("실패가 한도에 이르기 전까지는 막지 않고 한도에 이르면 막는다")
    void blocksOnceFailuresReachLimit() {
        AccountId accountId = new AccountId(ACCOUNT_IDS.incrementAndGet());
        for (int failure = 1; failure < RedisCompanyCodeAttemptLimiter.MAX_FAILURES; failure++) {
            limiter.recordFailure(accountId);
        }
        assertThat(limiter.isBlocked(accountId)).isFalse();

        limiter.recordFailure(accountId);

        assertThat(limiter.isBlocked(accountId)).isTrue();
    }

    @Test
    @DisplayName("다른 계정의 실패는 세지 않는다")
    void countsEachAccountSeparately() {
        AccountId attacker = new AccountId(ACCOUNT_IDS.incrementAndGet());
        for (int failure = 0; failure < RedisCompanyCodeAttemptLimiter.MAX_FAILURES; failure++) {
            limiter.recordFailure(attacker);
        }

        assertThat(limiter.isBlocked(new AccountId(ACCOUNT_IDS.incrementAndGet())))
                .isFalse();
    }

    @Test
    @DisplayName("첫 실패부터 시간 창이 흐르고 이후 실패가 창을 늘리지 않으며, 창이 끝나면 다시 확인할 수 있다")
    void keepsFixedWindowFromFirstFailure() {
        AccountId accountId = new AccountId(ACCOUNT_IDS.incrementAndGet());
        String key = RedisCompanyCodeAttemptLimiter.KEY_PREFIX + accountId.value();
        limiter.recordFailure(accountId);
        assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS))
                .isPositive()
                .isLessThanOrEqualTo(RedisCompanyCodeAttemptLimiter.WINDOW.toMillis());

        redisTemplate.expire(key, Duration.ofSeconds(1));
        for (int failure = 1; failure < RedisCompanyCodeAttemptLimiter.MAX_FAILURES; failure++) {
            limiter.recordFailure(accountId);
        }
        assertThat(limiter.isBlocked(accountId)).isTrue();
        assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)).isLessThanOrEqualTo(1_000);

        await().atMost(Duration.ofSeconds(5)).until(() -> !limiter.isBlocked(accountId));
    }

    @Test
    @DisplayName("Redis에 연결할 수 없으면 막지 않고 실패도 버린다")
    void failsOpenWhenRedisIsUnavailable() {
        LettuceConnectionFactory unreachable = new LettuceConnectionFactory("localhost", 1);
        unreachable.afterPropertiesSet();
        unreachable.start();
        try {
            RedisCompanyCodeAttemptLimiter disconnected =
                    new RedisCompanyCodeAttemptLimiter(new StringRedisTemplate(unreachable));
            AccountId accountId = new AccountId(ACCOUNT_IDS.incrementAndGet());

            assertThatCode(() -> disconnected.recordFailure(accountId)).doesNotThrowAnyException();
            assertThat(disconnected.isBlocked(accountId)).isFalse();
        } finally {
            unreachable.destroy();
        }
    }
}
