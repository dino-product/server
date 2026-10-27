package com.orbit.auth.adapter.out.redis;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.orbit.auth.application.port.out.ClosedAccountPort;
import com.orbit.auth.domain.AccountId;

@Component
class RedisClosedAccountAdapter implements ClosedAccountPort {

    static final String KEY_PREFIX = "auth:closed-account:";
    private static final String CLOSED = "1";

    private final StringRedisTemplate redisTemplate;

    RedisClosedAccountAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void markClosed(AccountId accountId, Duration retention) {
        redisTemplate.opsForValue().set(KEY_PREFIX + accountId.value(), CLOSED, retention);
    }

    @Override
    public boolean isClosed(AccountId accountId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + accountId.value()));
    }
}
