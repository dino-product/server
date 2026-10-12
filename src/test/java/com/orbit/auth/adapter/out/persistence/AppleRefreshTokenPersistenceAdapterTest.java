package com.orbit.auth.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.AppleRefreshToken;
import com.orbit.auth.application.port.out.AppleRefreshTokenRepository;
import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.support.TestcontainersConfiguration;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    TestcontainersConfiguration.class,
    AccountPersistenceAdapter.class,
    AppleRefreshTokenPersistenceAdapter.class,
    AppleRefreshTokenCipher.class,
    AppleRefreshTokenPersistenceAdapterTest.EncryptionProperties.class
})
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Apple refresh token 저장소")
class AppleRefreshTokenPersistenceAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-04T01:00:00Z");

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private AppleRefreshTokenRepository adapter;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("refresh token을 암호화해 저장하고 계정별로 복호화해 돌려준다")
    void storesEncryptedAndListsDecrypted() {
        AccountId accountId = newAccount();

        adapter.save(accountId, "com.orbit.app", "apple-refresh-1", NOW);
        entityManager.flush();
        entityManager.clear();

        assertThat(adapter.listByAccount(accountId))
                .containsExactly(new AppleRefreshToken("com.orbit.app", "apple-refresh-1"));
        Object stored = entityManager
                .createNativeQuery("select encrypted_token from apple_refresh_tokens where account_id = :accountId")
                .setParameter("accountId", accountId.value())
                .getSingleResult();
        assertThat((String) stored).startsWith("v1:").doesNotContain("apple-refresh-1");
    }

    @Test
    @DisplayName("같은 클라이언트의 refresh token은 최신 값으로 바꾸고 클라이언트별로 따로 둔다")
    void replacesPerClientAndKeepsClientsSeparate() {
        AccountId accountId = newAccount();

        adapter.save(accountId, "com.orbit.app", "old", NOW);
        adapter.save(accountId, "com.orbit.app", "new", NOW.plusSeconds(60));
        adapter.save(accountId, "com.orbit.web", "web", NOW.plusSeconds(120));
        entityManager.clear();

        assertThat(adapter.listByAccount(accountId))
                .containsExactlyInAnyOrder(
                        new AppleRefreshToken("com.orbit.app", "new"), new AppleRefreshToken("com.orbit.web", "web"));
    }

    @Test
    @DisplayName("저장한 값이 없는 계정은 빈 목록이다")
    void listsNothingForAccountWithoutTokens() {
        assertThat(adapter.listByAccount(newAccount())).isEmpty();
    }

    private AccountId newAccount() {
        ExternalIdentity identity = new ExternalIdentity(OAuthProvider.APPLE, "sub-" + UUID.randomUUID());
        return accounts.saveNew(Account.register(identity, NOW)).id().orElseThrow();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppleRefreshTokenEncryptionProperties.class)
    static class EncryptionProperties {}
}
