package com.orbit.auth.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.auth.application.port.out.PendingWebLogin;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.HashedNonce;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.auth.domain.PkceChallenge;
import com.orbit.support.RedisAdapterTestSupport;

@DisplayName("Redis 웹 로그인 교환 코드 저장소")
class RedisLoginExchangeAdapterTest extends RedisAdapterTestSupport {

    private static final OAuthProvider APPLE = OAuthProvider.APPLE;
    private static final PkceChallenge CHALLENGE = new PkceChallenge("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");

    private RedisLoginExchangeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisLoginExchangeAdapter(redisTemplate);
    }

    @Test
    @DisplayName("보관한 교환 코드는 계정과 등록 여부를 담아 한 번만 소비된다")
    void consumesExchangeCodeOnce() {
        String code = UUID.randomUUID().toString();
        adapter.save(APPLE, code, new PendingWebLogin(new AccountId(7L), true, CHALLENGE), Duration.ofSeconds(60));

        assertThat(adapter.consume(APPLE, code)).contains(new PendingWebLogin(new AccountId(7L), true, CHALLENGE));
        assertThat(adapter.consume(APPLE, code)).isEmpty();
    }

    @Test
    @DisplayName("보관하지 않은 교환 코드는 소비할 수 없고, 코드는 해시 키로 TTL과 함께 둔다")
    void rejectsUnknownAndKeepsTtl() {
        String code = UUID.randomUUID().toString();
        adapter.save(APPLE, code, new PendingWebLogin(new AccountId(8L), false, CHALLENGE), Duration.ofSeconds(60));

        assertThat(adapter.consume(APPLE, UUID.randomUUID().toString())).isEmpty();
        assertThat(redisTemplate.getExpire(RedisLoginExchangeAdapter.key(APPLE, code)))
                .isBetween(1L, 60L);
        assertThat(redisTemplate.hasKey(RedisLoginExchangeAdapter.keyPrefix(APPLE) + code))
                .isFalse();
    }

    @Test
    @DisplayName("한 제공자의 교환 코드는 다른 제공자로 소비할 수 없고 원래 코드도 지우지 않는다")
    void separatesCodesByProvider() {
        String code = UUID.randomUUID().toString();
        PendingWebLogin login = new PendingWebLogin(new AccountId(9L), false, CHALLENGE);
        adapter.save(OAuthProvider.KAKAO, code, login, Duration.ofSeconds(60));

        assertThat(adapter.consume(APPLE, code)).isEmpty();
        assertThat(adapter.consume(OAuthProvider.KAKAO, code)).contains(login);
    }

    @Test
    @DisplayName("Apple 교환 코드는 공통화 전과 같은 키에 둔다")
    void keepsAppleKeyName() {
        String code = UUID.randomUUID().toString();
        adapter.save(APPLE, code, new PendingWebLogin(new AccountId(7L), true, CHALLENGE), Duration.ofSeconds(60));

        assertThat(redisTemplate.hasKey(
                        "auth:apple-login-exchange:" + HashedNonce.fromRaw(code).value()))
                .isTrue();
        assertThat(RedisLoginExchangeAdapter.keyPrefix(OAuthProvider.KAKAO)).isEqualTo("auth:kakao-login-exchange:");
    }
}
