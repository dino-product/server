package com.orbit.auth.adapter.out.redis;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.orbit.auth.application.port.out.LoginExchangePort;
import com.orbit.auth.application.port.out.PendingWebLogin;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.HashedNonce;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.auth.domain.PkceChallenge;

/**
 * 교환 코드는 제공자별 키 공간(예: Apple은 {@code auth:apple-login-exchange:})에 SHA-256 해시로만 둔다. 값에는 계정·등록 여부·PKCE challenge만
 * 있고 Access Token은 없다.
 */
@Component
class RedisLoginExchangeAdapter implements LoginExchangePort {

    private static final String KEY_SUFFIX = "-login-exchange:";
    private static final String SEPARATOR = ":";

    private final StringRedisTemplate redisTemplate;

    RedisLoginExchangeAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(OAuthProvider provider, String code, PendingWebLogin login, Duration ttl) {
        String value = login.accountId().value()
                + SEPARATOR
                + login.registered()
                + SEPARATOR
                + login.codeChallenge().value();
        redisTemplate.opsForValue().set(key(provider, code), value, ttl);
    }

    @Override
    public Optional<PendingWebLogin> consume(OAuthProvider provider, String code) {
        // GETDEL로 조회와 삭제를 원자적으로 처리해 같은 코드로 토큰을 두 번 받지 못하게 한다.
        String value = redisTemplate.opsForValue().getAndDelete(key(provider, code));
        if (value == null) {
            return Optional.empty();
        }
        String[] fields = value.split(SEPARATOR, 3);
        return Optional.of(new PendingWebLogin(
                new AccountId(Long.parseLong(fields[0])),
                Boolean.parseBoolean(fields[1]),
                new PkceChallenge(fields[2])));
    }

    static String keyPrefix(OAuthProvider provider) {
        return "auth:" + provider.name().toLowerCase(Locale.ROOT) + KEY_SUFFIX;
    }

    static String key(OAuthProvider provider, String code) {
        return keyPrefix(provider) + HashedNonce.fromRaw(code).value();
    }
}
