package com.orbit.auth.adapter.out.redis;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.orbit.auth.application.port.out.WebLoginState;
import com.orbit.auth.application.port.out.WebLoginStatePort;
import com.orbit.auth.domain.HashedNonce;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.auth.domain.PkceChallenge;

/** 제공자마다 키 공간을 나눈다(예: Apple은 {@code auth:apple-web-login:}). */
@Component
class RedisWebLoginStateAdapter implements WebLoginStatePort {

    private static final String KEY_SUFFIX = "-web-login:";
    private static final String SEPARATOR = "|";
    private static final int FIELDS = 3;

    private final StringRedisTemplate redisTemplate;

    RedisWebLoginStateAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(
            OAuthProvider provider, String state, String browserBindingHash, WebLoginState pending, Duration ttl) {
        // 복귀 주소는 '|'를 포함할 수 있으므로 마지막 필드에 둔다.
        String value =
                pending.nonce().value() + SEPARATOR + pending.codeChallenge().value() + SEPARATOR + pending.returnUri();
        redisTemplate.opsForValue().set(key(provider, state, browserBindingHash), value, ttl);
    }

    @Override
    public Optional<WebLoginState> consume(OAuthProvider provider, String state, String browserBindingHash) {
        // GETDEL로 조회와 삭제를 원자적으로 처리해 같은 콜백이 두 번 처리되지 않게 한다.
        String value = redisTemplate.opsForValue().getAndDelete(key(provider, state, browserBindingHash));
        if (value == null) {
            return Optional.empty();
        }
        String[] fields = value.split("\\" + SEPARATOR, FIELDS);
        return Optional.of(new WebLoginState(new HashedNonce(fields[0]), fields[2], new PkceChallenge(fields[1])));
    }

    static String keyPrefix(OAuthProvider provider) {
        return "auth:" + provider.name().toLowerCase(Locale.ROOT) + KEY_SUFFIX;
    }

    static String key(OAuthProvider provider, String state, String browserBindingHash) {
        return keyPrefix(provider) + state + ":" + browserBindingHash;
    }
}
