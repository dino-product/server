package com.orbit.auth.adapter.out.redis;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.orbit.auth.application.port.out.RevokedAccessTokenPort;

@Component
class RedisRevokedAccessTokenAdapter implements RevokedAccessTokenPort {

    static final String KEY_PREFIX = "auth:revoked-access-token:";
    private static final String REVOKED = "1";

    private final StringRedisTemplate redisTemplate;

    RedisRevokedAccessTokenAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void revoke(String tokenId, Duration remainingLifetime) {
        if (remainingLifetime.isZero() || remainingLifetime.isNegative()) {
            // 이미 만료된 토큰은 검증기가 거부하므로 기억할 필요가 없다.
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + tokenId, REVOKED, remainingLifetime);
    }

    @Override
    public boolean isRevoked(String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + tokenId));
    }
}
