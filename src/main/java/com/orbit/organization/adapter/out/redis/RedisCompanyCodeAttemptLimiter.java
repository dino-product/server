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
 * 계정별로 첫 실패부터 10분 동안 실패를 세고 10회에 이르면 그 10분이 끝날 때까지 막는다(고정 창). 회사 코드 6자리는 약 10억 가지라 한 계정이 하루 1,440회
 * 안에서 맞힐 가능성은 낮다. Redis에 닿지 못하면 회사 확인·참여 요청을 막지 않도록 제한 없이 통과시킨다.
 */
@Slf4j
@Component
class RedisCompanyCodeAttemptLimiter implements CompanyCodeAttemptLimiter {

    static final String KEY_PREFIX = "organization:company-code-failures:";
    static final int MAX_FAILURES = 10;
    static final Duration WINDOW = Duration.ofMinutes(10);

    // 증가와 만료 설정을 한 스크립트로 묶어 그 사이에 멈춰도 만료 없는 키가 남아 계정이 계속 막히지 않게 한다.
    private static final RedisScript<Long> RECORD_FAILURE = RedisScript.of("""
            local failures = redis.call('INCR', KEYS[1])
            if failures == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return failures
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    RedisCompanyCodeAttemptLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean isBlocked(AccountId accountId) {
        try {
            String failures = redisTemplate.opsForValue().get(key(accountId));
            return failures != null && Long.parseLong(failures) >= MAX_FAILURES;
        } catch (DataAccessException exception) {
            log.warn("Company code attempt limit check skipped", exception);
            return false;
        }
    }

    @Override
    public void recordFailure(AccountId accountId) {
        try {
            redisTemplate.execute(RECORD_FAILURE, List.of(key(accountId)), String.valueOf(WINDOW.toMillis()));
        } catch (DataAccessException exception) {
            log.warn("Company code attempt failure not recorded", exception);
        }
    }

    private static String key(AccountId accountId) {
        return KEY_PREFIX + accountId.value();
    }
}
