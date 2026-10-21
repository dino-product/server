package com.orbit.auth.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.ProviderUnlinkRepository;
import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.auth.domain.ProviderUnlink;
import com.orbit.support.PersistenceAdapterTestSupport;

@DisplayName("외부 연결 해제 대기 저장소")
class ProviderUnlinkPersistenceAdapterTest extends PersistenceAdapterTestSupport {

    private static final Instant REGISTERED_AT = Instant.parse("2026-09-27T01:00:00Z");
    private static final Instant REQUESTED_AT = Instant.parse("2026-10-11T03:00:00Z");

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private ProviderUnlinkRepository adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("닫은 계정의 제공자별 회원번호와 요청 시각을 남긴다")
    void savesProviderUnlinks() {
        ExternalIdentity kakao =
                new ExternalIdentity(OAuthProvider.KAKAO, UUID.randomUUID().toString());
        ExternalIdentity apple =
                new ExternalIdentity(OAuthProvider.APPLE, UUID.randomUUID().toString());
        AccountId accountId =
                accounts.saveNew(Account.register(kakao, REGISTERED_AT)).id().orElseThrow();

        adapter.saveAll(List.of(
                new ProviderUnlink(accountId, kakao, REQUESTED_AT),
                new ProviderUnlink(accountId, apple, REQUESTED_AT)));

        assertThat(jdbcTemplate.query(
                        "select provider, oauth_uid, requested_at from provider_unlinks where account_id = ?"
                                + " order by provider",
                        (rs, row) -> new ProviderUnlink(
                                accountId,
                                new ExternalIdentity(OAuthProvider.valueOf(rs.getString(1)), rs.getString(2)),
                                rs.getObject(3, Timestamp.class).toInstant()),
                        accountId.value()))
                .containsExactly(
                        new ProviderUnlink(accountId, apple, REQUESTED_AT),
                        new ProviderUnlink(accountId, kakao, REQUESTED_AT));
    }
}
