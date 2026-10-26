package com.orbit.auth.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.RefreshSessionRepository;
import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.auth.domain.RefreshSession;
import com.orbit.auth.domain.RefreshSessionId;
import com.orbit.auth.domain.RefreshTokenHash;
import com.orbit.support.PersistenceAdapterTestSupport;

@DisplayName("기기 세션 저장소")
class RefreshSessionPersistenceAdapterTest extends PersistenceAdapterTestSupport {

    private static final Instant REGISTERED_AT = Instant.parse("2026-09-27T01:00:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-10-17T01:00:00Z");
    private static final Duration IDLE = Duration.ofDays(30);
    private static final RefreshTokenHash FIRST = RefreshTokenHash.of("first-secret");
    private static final RefreshTokenHash SECOND = RefreshTokenHash.of("second-secret");

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private RefreshSessionRepository adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("새 세션을 해시와 시각만으로 저장하고 잠그고 다시 읽는다")
    void savesAndFindsSession() {
        AccountId accountId = newAccount();
        RefreshSession session = RefreshSession.start(newSessionId(), accountId, FIRST, STARTED_AT, IDLE);

        adapter.saveNew(session);

        assertThat(adapter.findByIdForUpdate(session.id())).hasValueSatisfying(found -> {
            assertThat(found.accountId()).isEqualTo(accountId);
            assertThat(found.tokenHash()).isEqualTo(FIRST);
            assertThat(found.previousTokenHash()).isEmpty();
            assertThat(found.rotatedAt()).isEmpty();
            assertThat(found.startedAt()).isEqualTo(STARTED_AT);
            assertThat(found.expiresAt()).isEqualTo(STARTED_AT.plus(IDLE));
        });
        assertThat(jdbcTemplate.queryForObject(
                        "select token_hash from refresh_sessions where id = ?",
                        String.class,
                        session.id().value()))
                .isEqualTo(FIRST.value());
    }

    @Test
    @DisplayName("회전한 상태를 저장한다")
    void updatesRotatedSession() {
        RefreshSession session = RefreshSession.start(newSessionId(), newAccount(), FIRST, STARTED_AT, IDLE);
        adapter.saveNew(session);
        RefreshSession locked = adapter.findByIdForUpdate(session.id()).orElseThrow();
        Instant rotatedAt = STARTED_AT.plusSeconds(3600);
        locked.refresh(FIRST, SECOND, rotatedAt, IDLE);

        adapter.update(locked);
        entityManager.flush();
        entityManager.clear();

        assertThat(jdbcTemplate.queryForMap(
                        "select token_hash, previous_token_hash, rotated_at, expires_at from refresh_sessions"
                                + " where id = ?",
                        session.id().value()))
                .containsEntry("token_hash", SECOND.value())
                .containsEntry("previous_token_hash", FIRST.value())
                .containsEntry("rotated_at", Timestamp.from(rotatedAt))
                .containsEntry("expires_at", Timestamp.from(rotatedAt.plus(IDLE)));
    }

    @Test
    @DisplayName("없는 세션은 빈 값이다")
    void findsNothingForUnknownSession() {
        assertThat(adapter.findByIdForUpdate(newSessionId())).isEmpty();
    }

    @Test
    @DisplayName("세션 하나를 지운다")
    void deletesSession() {
        RefreshSession session = RefreshSession.start(newSessionId(), newAccount(), FIRST, STARTED_AT, IDLE);
        adapter.saveNew(session);

        adapter.delete(session.id());

        assertThat(adapter.findByIdForUpdate(session.id())).isEmpty();
    }

    @Test
    @DisplayName("다른 계정의 세션 식별자로는 지우지 않는다")
    void deletesOnlyOwnedSession() {
        AccountId owner = newAccount();
        AccountId other = newAccount();
        RefreshSession session = RefreshSession.start(newSessionId(), owner, FIRST, STARTED_AT, IDLE);
        adapter.saveNew(session);

        adapter.deleteOwnedBy(session.id(), other);
        assertThat(adapter.findByIdForUpdate(session.id())).isPresent();

        adapter.deleteOwnedBy(session.id(), owner);
        assertThat(adapter.findByIdForUpdate(session.id())).isEmpty();
    }

    @Test
    @DisplayName("현재 또는 직전 토큰 해시가 맞을 때만 세션을 지운다")
    void deletesSessionMatchingTokenHash() {
        RefreshSession current = RefreshSession.start(newSessionId(), newAccount(), FIRST, STARTED_AT, IDLE);
        RefreshSession rotated = RefreshSession.start(newSessionId(), newAccount(), FIRST, STARTED_AT, IDLE);
        adapter.saveNew(current);
        adapter.saveNew(rotated);
        RefreshSession locked = adapter.findByIdForUpdate(rotated.id()).orElseThrow();
        locked.refresh(FIRST, SECOND, STARTED_AT.plusSeconds(60), IDLE);
        adapter.update(locked);

        adapter.deleteByToken(current.id(), SECOND);
        assertThat(adapter.findByIdForUpdate(current.id())).isPresent();

        adapter.deleteByToken(current.id(), FIRST);
        adapter.deleteByToken(rotated.id(), FIRST);
        assertThat(adapter.findByIdForUpdate(current.id())).isEmpty();
        assertThat(adapter.findByIdForUpdate(rotated.id())).isEmpty();
    }

    @Test
    @DisplayName("계정의 모든 세션을 지우고 다른 계정의 세션은 남긴다")
    void deletesAllSessionsOfAccount() {
        AccountId accountId = newAccount();
        AccountId other = newAccount();
        RefreshSession first = RefreshSession.start(newSessionId(), accountId, FIRST, STARTED_AT, IDLE);
        RefreshSession second = RefreshSession.start(newSessionId(), accountId, SECOND, STARTED_AT, IDLE);
        RefreshSession others = RefreshSession.start(newSessionId(), other, FIRST, STARTED_AT, IDLE);
        adapter.saveNew(first);
        adapter.saveNew(second);
        adapter.saveNew(others);
        // 한 세션만 회전시켜 만료 시각이 다른 세션을 함께 둔다.
        RefreshSession rotated = adapter.findByIdForUpdate(second.id()).orElseThrow();
        rotated.refresh(SECOND, FIRST, STARTED_AT.plus(Duration.ofDays(10)), IDLE);
        adapter.update(rotated);

        adapter.deleteAllByAccount(accountId);

        assertThat(adapter.findByIdForUpdate(first.id())).isEmpty();
        assertThat(adapter.findByIdForUpdate(second.id())).isEmpty();
        assertThat(adapter.findByIdForUpdate(others.id())).isPresent();
    }

    @Test
    @DisplayName("계정의 세션을 모두 지워도 같은 트랜잭션의 다른 Entity는 관리 상태로 남고 그 변경은 DB에 반영된다")
    void keepsCallerEntitiesManagedWhenDeletingAllSessions() {
        AccountId accountId = newAccount();
        adapter.saveNew(RefreshSession.start(newSessionId(), accountId, FIRST, STARTED_AT, IDLE));
        AccountJpaEntity account = entityManager.find(AccountJpaEntity.class, accountId.value());
        Instant closedAt = STARTED_AT.plusSeconds(60);
        account.apply(Account.reconstituteClosed(accountId, REGISTERED_AT, closedAt));

        adapter.deleteAllByAccount(accountId);

        assertThat(entityManager.getEntityManager().contains(account)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "select closed_at from accounts where id = ?", Timestamp.class, accountId.value()))
                .isEqualTo(Timestamp.from(closedAt));
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from refresh_sessions where account_id = ?", Integer.class, accountId.value()))
                .isZero();
    }

    @Test
    @DisplayName("계정의 만료된 세션만 지운다(만료 시각 정각 포함)")
    void deletesExpiredSessionsOfAccount() {
        AccountId accountId = newAccount();
        RefreshSession expired = RefreshSession.start(newSessionId(), accountId, FIRST, STARTED_AT, IDLE);
        RefreshSession live = RefreshSession.start(newSessionId(), accountId, SECOND, STARTED_AT.plusSeconds(1), IDLE);
        adapter.saveNew(expired);
        adapter.saveNew(live);

        adapter.deleteExpiredByAccount(accountId, STARTED_AT.plus(IDLE));

        assertThat(adapter.findByIdForUpdate(expired.id())).isEmpty();
        assertThat(adapter.findByIdForUpdate(live.id())).isPresent();
    }

    private AccountId newAccount() {
        return accounts.saveNew(Account.register(
                        new ExternalIdentity(
                                OAuthProvider.KAKAO, UUID.randomUUID().toString()),
                        REGISTERED_AT))
                .id()
                .orElseThrow();
    }

    private static RefreshSessionId newSessionId() {
        return new RefreshSessionId(UUID.randomUUID().toString());
    }
}
