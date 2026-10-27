package com.orbit.auth.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.ProviderUnlinkRepository;
import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.auth.domain.ProviderUnlink;
import com.orbit.support.PersistenceAdapterTestSupport;

/** 다른 테스트 클래스의 대기와 섞이지 않도록 기한 조회는 아주 먼 과거 시각을 기준으로 삼는다. */
@DisplayName("외부 연결 해제 대기 저장소")
class ProviderUnlinkPersistenceAdapterTest extends PersistenceAdapterTestSupport {

    private static final Instant REGISTERED_AT = Instant.parse("2026-09-27T01:00:00Z");
    private static final Instant LONG_AGO = Instant.parse("2000-01-01T00:00:00Z");

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private ProviderUnlinkRepository adapter;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("카카오는 회원번호와 함께, Apple은 회원번호 없이 저장하고 계정별로 다시 읽는다")
    void savesAndListsByAccount() {
        AccountId accountId = newAccount();
        ProviderUnlink kakao = ProviderUnlink.request(accountId, kakaoIdentity(), LONG_AGO);
        ProviderUnlink apple = ProviderUnlink.request(accountId, appleIdentity(), LONG_AGO);

        adapter.saveAll(List.of(kakao, apple));
        flushAndClear();

        assertThat(adapter.listByAccount(accountId)).containsExactlyInAnyOrder(kakao, apple);
        assertThat(adapter.listByAccount(newAccount())).isEmpty();
    }

    @Test
    @DisplayName("다음 시도 시각이 지난 대기만 오래된 순으로 정한 개수까지 읽는다")
    void listsDueUnlinksOldestFirst() {
        AccountId first = newAccount();
        AccountId second = newAccount();
        AccountId notYet = newAccount();
        ProviderUnlink older = ProviderUnlink.request(first, kakaoIdentity(), LONG_AGO);
        ProviderUnlink newer = ProviderUnlink.request(second, kakaoIdentity(), LONG_AGO.plusSeconds(10));
        adapter.saveAll(List.of(
                newer, older, ProviderUnlink.request(notYet, kakaoIdentity(), LONG_AGO.plus(Duration.ofDays(1)))));
        flushAndClear();
        Instant now = LONG_AGO.plus(Duration.ofHours(1));

        assertThat(adapter.listDue(now, 10)).containsExactly(older, newer);
        assertThat(adapter.listDue(now, 1)).containsExactly(older);
    }

    @Test
    @DisplayName("실패 뒤 재시도 상태를 바꾸고, 지운 대기는 다시 읽지 않는다. 이미 없는 대기를 바꾸거나 지워도 오류가 없다")
    void updatesAndDeletes() {
        AccountId accountId = newAccount();
        ProviderUnlink kakao = ProviderUnlink.request(accountId, kakaoIdentity(), LONG_AGO);
        ProviderUnlink apple = ProviderUnlink.request(accountId, appleIdentity(), LONG_AGO);
        adapter.saveAll(List.of(kakao, apple));
        ProviderUnlink retried = kakao.afterFailure(LONG_AGO.plusSeconds(60)).orElseThrow();

        adapter.update(retried);
        adapter.delete(accountId, OAuthProvider.APPLE);
        flushAndClear();

        assertThat(adapter.listByAccount(accountId)).containsExactly(retried);
        adapter.delete(accountId, OAuthProvider.APPLE);
        adapter.update(apple);
        assertThat(adapter.listByAccount(accountId)).containsExactly(retried);
    }

    private AccountId newAccount() {
        return accounts.saveNew(Account.register(kakaoIdentity(), REGISTERED_AT))
                .id()
                .orElseThrow();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static ExternalIdentity kakaoIdentity() {
        return new ExternalIdentity(OAuthProvider.KAKAO, UUID.randomUUID().toString());
    }

    private static ExternalIdentity appleIdentity() {
        return new ExternalIdentity(OAuthProvider.APPLE, UUID.randomUUID().toString());
    }
}
