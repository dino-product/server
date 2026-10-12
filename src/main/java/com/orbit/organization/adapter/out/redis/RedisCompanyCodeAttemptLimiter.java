package com.orbit.organization.adapter.out.redis;

import java.time.Duration;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.orbit.organization.application.port.out.CompanyCodeAttemptLimiter;
import com.orbit.organization.domain.AccountId;

import lombok.extern.slf4j.Slf4j;

/**
 * 계정별로 첫 시도부터 10분 동안 실패를 세고 10회에 이르면 그 10분이 끝날 때까지 막는다(고정 창). 회사 코드 6자리는 약 10억 가지라 한 계정이 하루 1,440회
 * 안에서 맞힐 가능성은 낮다. Redis에 닿지 못하면 회사 확인·참여 요청을 막지 않도록 제한 없이 통과시킨다.
 */
@Slf4j
@Component
class RedisCompanyCodeAttemptLimiter implements CompanyCodeAttemptLimiter {

    static final String KEY_PREFIX = "organization:company-code-failures:";
    static final int MAX_FAILURES = 10;
    static final Duration WINDOW = Duration.ofMinutes(10);

    // 증가와 만료 설정을 한 스크립트로 묶어 그 사이에 멈춰도 만료 없는 키가 남아 계정이 계속 막히지 않게 한다.
    private static final RedisScript<Long> TRY_ATTEMPT = RedisScript.of("""
            local attempts = redis.call('INCR', KEYS[1])
            if attempts == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return attempts
            """, Long.class);

    // 창이 끝나 키가 사라진 뒤에 되돌리면 만료 없는 음수 키가 생기므로 키가 있을 때만 줄인다.
    private static final RedisScript<Long> REFUND = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return redis.call('DECR', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    RedisCompanyCodeAttemptLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAttempt(AccountId accountId) {
        try {
            Long attempts =
                    redisTemplate.execute(TRY_ATTEMPT, List.of(key(accountId)), String.valueOf(WINDOW.toMillis()));
            return attempts == null || attempts <= MAX_FAILURES;
        } catch (DataAccessException exception) {
            log.warn("Company code attempt limit check skipped", exception);
            return true;
        }
    }

    @Override
    public void refund(AccountId accountId) {
        try {
            redisTemplate.execute(REFUND, List.of(key(accountId)));
        } catch (DataAccessException exception) {
            log.warn("Company code attempt refund skipped", exception);
        }
    }

    private static String key(AccountId accountId) {
        return KEY_PREFIX + accountId.value();
    }
}
