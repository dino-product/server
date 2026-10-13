package com.orbit.organization.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    @DisplayName("되돌리지 않은 시도가 한도에 이르기까지는 허용하고 그다음 시도부터 거부한다")
    void rejectsAttemptsBeyondLimit() {
        AccountId accountId = new AccountId(ACCOUNT_IDS.incrementAndGet());
        for (int attempt = 0; attempt < RedisCompanyCodeAttemptLimiter.MAX_FAILURES; attempt++) {
            assertThat(limiter.tryAttempt(accountId)).isTrue();
        }

        assertThat(limiter.tryAttempt(accountId)).isFalse();
    }

    @Test
    @DisplayName("되돌린 시도는 실패로 남지 않는다")
    void refundedAttemptsDoNotCount() {
        AccountId accountId = new AccountId(ACCOUNT_IDS.incrementAndGet());
        for (int attempt = 0; attempt < RedisCompanyCodeAttemptLimiter.MAX_FAILURES * 2; attempt++) {
            assertThat(limiter.tryAttempt(accountId)).isTrue();
            limiter.refund(accountId);
        }

        assertThat(limiter.tryAttempt(accountId)).isTrue();
    }

    @Test
    @DisplayName("다른 계정의 실패는 세지 않는다")
    void countsEachAccountSeparately() {
        AccountId attacker = new AccountId(ACCOUNT_IDS.incrementAndGet());
        for (int attempt = 0; attempt <= RedisCompanyCodeAttemptLimiter.MAX_FAILURES; attempt++) {
            limiter.tryAttempt(attacker);
        }

        assertThat(limiter.tryAttempt(new AccountId(ACCOUNT_IDS.incrementAndGet())))
                .isTrue();
    }

    @Test
    @DisplayName("한꺼번에 몰아 보낸 시도도 한도만큼만 허용한다")
    void allowsOnlyLimitUnderConcurrentAttempts() throws Exception {
        AccountId accountId = new AccountId(ACCOUNT_IDS.incrementAndGet());
        int attempts = 50;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int attempt = 0; attempt < attempts; attempt++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return limiter.tryAttempt(accountId);
                }));
            }
            start.countDown();
            long allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    allowed++;
                }
            }

            assertThat(allowed).isEqualTo(RedisCompanyCodeAttemptLimiter.MAX_FAILURES);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("첫 시도부터 시간 창이 흐르고 이후 시도가 창을 늘리지 않으며, 창이 끝나면 다시 시도할 수 있다")
    void keepsFixedWindowFromFirstAttempt() {
        AccountId accountId = new AccountId(ACCOUNT_IDS.incrementAndGet());
        String key = RedisCompanyCodeAttemptLimiter.KEY_PREFIX + accountId.value();
        limiter.tryAttempt(accountId);
        assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS))
                .isPositive()
                .isLessThanOrEqualTo(RedisCompanyCodeAttemptLimiter.WINDOW.toMillis());

        redisTemplate.expire(key, Duration.ofSeconds(1));
        for (int attempt = 1; attempt < RedisCompanyCodeAttemptLimiter.MAX_FAILURES; attempt++) {
            limiter.tryAttempt(accountId);
        }
        assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)).isLessThanOrEqualTo(1_000);
        assertThat(limiter.tryAttempt(accountId)).isFalse();

        await().atMost(Duration.ofSeconds(5)).until(() -> !Boolean.TRUE.equals(redisTemplate.hasKey(key)));
        assertThat(limiter.tryAttempt(accountId)).isTrue();
    }

    @Test
    @DisplayName("창이 끝난 뒤 되돌려도 만료 없는 키를 만들지 않는다")
    void refundAfterWindowLeavesNoKey() {
        AccountId accountId = new AccountId(ACCOUNT_IDS.incrementAndGet());

        limiter.refund(accountId);

        assertThat(redisTemplate.hasKey(RedisCompanyCodeAttemptLimiter.KEY_PREFIX + accountId.value()))
                .isFalse();
    }

    @Test
    @DisplayName("Redis에 연결할 수 없으면 막지 않는다")
    void failsOpenWhenRedisIsUnavailable() {
        LettuceConnectionFactory unreachable = new LettuceConnectionFactory("localhost", 1);
        unreachable.afterPropertiesSet();
        unreachable.start();
        try {
            RedisCompanyCodeAttemptLimiter disconnected =
                    new RedisCompanyCodeAttemptLimiter(new StringRedisTemplate(unreachable));
            AccountId accountId = new AccountId(ACCOUNT_IDS.incrementAndGet());

            assertThat(disconnected.tryAttempt(accountId)).isTrue();
            assertThatCode(() -> disconnected.refund(accountId)).doesNotThrowAnyException();
        } finally {
            unreachable.destroy();
        }
    }
}
