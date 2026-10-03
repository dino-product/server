package com.orbit.auth.adapter.out.redis;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.orbit.auth.application.port.out.LoginNoncePort;

@Component
class RedisLoginNonceAdapter implements LoginNoncePort {

    static final String KEY_PREFIX = "auth:login-nonce:";
    private static final String PRESENT = "1";

    private final StringRedisTemplate redisTemplate;

    RedisLoginNonceAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void store(String nonce, Duration ttl) {
        redisTemplate.opsForValue().set(KEY_PREFIX + nonce, PRESENT, ttl);
    }

    @Override
    public boolean consume(String nonce) {
        // GETDEL로 조회와 삭제를 원자적으로 처리해 동시 요청이 같은 nonce를 두 번 쓰지 못하게 한다.
        return redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + nonce) != null;
    }
}
