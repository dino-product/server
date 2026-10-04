package com.orbit.auth.adapter.out.redis;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.orbit.auth.application.port.out.AppleLoginExchangePort;
import com.orbit.auth.application.port.out.PendingAppleLogin;
import com.orbit.auth.domain.AccountId;

@Component
class RedisAppleLoginExchangeAdapter implements AppleLoginExchangePort {

    static final String KEY_PREFIX = "auth:apple-login-exchange:";
    private static final String SEPARATOR = ":";

    private final StringRedisTemplate redisTemplate;

    RedisAppleLoginExchangeAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String code, PendingAppleLogin login, Duration ttl) {
        redisTemplate
                .opsForValue()
                .set(KEY_PREFIX + code, login.accountId().value() + SEPARATOR + login.registered(), ttl);
    }

    @Override
    public Optional<PendingAppleLogin> consume(String code) {
        // GETDEL로 조회와 삭제를 원자적으로 처리해 같은 코드로 토큰을 두 번 받지 못하게 한다.
        String value = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + code);
        if (value == null) {
            return Optional.empty();
        }
        String[] fields = value.split(SEPARATOR, 2);
        return Optional.of(
                new PendingAppleLogin(new AccountId(Long.parseLong(fields[0])), Boolean.parseBoolean(fields[1])));
    }
}
