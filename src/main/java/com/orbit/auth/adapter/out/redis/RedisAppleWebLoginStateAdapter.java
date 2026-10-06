package com.orbit.auth.adapter.out.redis;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.orbit.auth.application.port.out.AppleWebLoginState;
import com.orbit.auth.application.port.out.AppleWebLoginStatePort;
import com.orbit.auth.domain.HashedNonce;
import com.orbit.auth.domain.PkceChallenge;

@Component
class RedisAppleWebLoginStateAdapter implements AppleWebLoginStatePort {

    static final String KEY_PREFIX = "auth:apple-web-login:";
    private static final String SEPARATOR = "|";
    private static final int FIELDS = 3;

    private final StringRedisTemplate redisTemplate;

    RedisAppleWebLoginStateAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String state, String browserBindingHash, AppleWebLoginState pending, Duration ttl) {
        // 복귀 주소는 '|'를 포함할 수 있으므로 마지막 필드에 둔다.
        String value =
                pending.nonce().value() + SEPARATOR + pending.codeChallenge().value() + SEPARATOR + pending.returnUri();
        redisTemplate.opsForValue().set(key(state, browserBindingHash), value, ttl);
    }

    @Override
    public Optional<AppleWebLoginState> consume(String state, String browserBindingHash) {
        // GETDEL로 조회와 삭제를 원자적으로 처리해 같은 콜백이 두 번 처리되지 않게 한다.
        String value = redisTemplate.opsForValue().getAndDelete(key(state, browserBindingHash));
        if (value == null) {
            return Optional.empty();
        }
        String[] fields = value.split("\\" + SEPARATOR, FIELDS);
        return Optional.of(new AppleWebLoginState(new HashedNonce(fields[0]), fields[2], new PkceChallenge(fields[1])));
    }

    static String key(String state, String browserBindingHash) {
        return KEY_PREFIX + state + ":" + browserBindingHash;
    }
}
