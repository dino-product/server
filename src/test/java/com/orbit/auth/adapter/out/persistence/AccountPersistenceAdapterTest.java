package com.orbit.auth.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.support.TestcontainersConfiguration;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("계정 저장소")
class AccountPersistenceAdapterTest {

    private static final ExternalIdentity KAKAO_IDENTITY = new ExternalIdentity(OAuthProvider.KAKAO, "1234567890");
    private static final Instant REGISTERED_AT = Instant.parse("2026-09-27T01:00:00Z");

    @Autowired
    private SpringDataAccountRepository springDataRepository;

    private AccountPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AccountPersistenceAdapter(springDataRepository);
    }

    @Test
    @DisplayName("계정을 저장하고 외부 식별로 다시 찾는다")
    void savesAndFindsByIdentity() {
        Account saved = adapter.save(Account.register(KAKAO_IDENTITY, REGISTERED_AT));

        assertThat(saved.id()).isPresent();
        assertThat(adapter.findByIdentity(KAKAO_IDENTITY)).hasValueSatisfying(found -> {
            assertThat(found.id()).isEqualTo(saved.id());
            assertThat(found.identities()).containsExactly(KAKAO_IDENTITY);
            assertThat(found.registeredAt()).isEqualTo(REGISTERED_AT);
        });
    }

    @Test
    @DisplayName("연결되지 않은 외부 식별은 찾지 못한다")
    void returnsEmptyForUnknownIdentity() {
        adapter.save(Account.register(KAKAO_IDENTITY, REGISTERED_AT));

        assertThat(adapter.findByIdentity(new ExternalIdentity(OAuthProvider.KAKAO, "other")))
                .isEmpty();
    }

    @Test
    @DisplayName("같은 외부 식별로 두 계정을 저장하면 거부한다")
    void rejectsDuplicateIdentity() {
        adapter.save(Account.register(KAKAO_IDENTITY, REGISTERED_AT));

        assertThatThrownBy(() -> {
                    adapter.save(Account.register(KAKAO_IDENTITY, REGISTERED_AT));
                    springDataRepository.flush();
                })
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
