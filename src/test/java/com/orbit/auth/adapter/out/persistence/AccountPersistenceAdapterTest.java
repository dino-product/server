package com.orbit.auth.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.DuplicateIdentityException;
import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.support.TestcontainersConfiguration;

/** saveNew는 별도 트랜잭션으로 확정되므로 테스트마다 다른 외부 식별을 쓴다. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, AccountPersistenceAdapter.class})
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("계정 저장소")
class AccountPersistenceAdapterTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-09-27T01:00:00Z");

    @Autowired
    private AccountRepository adapter;

    @Test
    @DisplayName("계정을 저장하고 외부 식별로 다시 찾는다")
    void savesAndFindsByIdentity() {
        ExternalIdentity identity = newIdentity();

        Account saved = adapter.saveNew(Account.register(identity, REGISTERED_AT));

        assertThat(saved.id()).isPresent();
        assertThat(adapter.findByIdentity(identity)).hasValueSatisfying(found -> {
            assertThat(found.id()).isEqualTo(saved.id());
            assertThat(found.identities()).containsExactly(identity);
            assertThat(found.registeredAt()).isEqualTo(REGISTERED_AT);
        });
    }

    @Test
    @DisplayName("연결되지 않은 외부 식별은 찾지 못한다")
    void returnsEmptyForUnknownIdentity() {
        adapter.saveNew(Account.register(newIdentity(), REGISTERED_AT));

        assertThat(adapter.findByIdentity(newIdentity())).isEmpty();
    }

    @Test
    @DisplayName("같은 외부 식별로 두 계정을 저장하면 중복 식별 예외로 거부하고 기존 계정은 그대로 찾는다")
    void rejectsDuplicateIdentity() {
        ExternalIdentity identity = newIdentity();
        Account first = adapter.saveNew(Account.register(identity, REGISTERED_AT));

        assertThatThrownBy(() -> adapter.saveNew(Account.register(identity, REGISTERED_AT)))
                .isInstanceOfSatisfying(DuplicateIdentityException.class, exception -> assertThat(exception.identity())
                        .isEqualTo(identity));
        assertThat(adapter.findByIdentity(identity)).map(Account::id).contains(first.id());
    }

    @Test
    @DisplayName("이미 식별자가 있는 계정은 새로 저장하지 않는다")
    void rejectsExistingAccount() {
        Account existing = Account.reconstitute(new AccountId(1L), List.of(newIdentity()), REGISTERED_AT);

        assertThatThrownBy(() -> adapter.saveNew(existing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("only new accounts can be saved");
    }

    private static ExternalIdentity newIdentity() {
        return new ExternalIdentity(OAuthProvider.KAKAO, UUID.randomUUID().toString());
    }
}
