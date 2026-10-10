package com.orbit.profile.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.profile.application.port.out.ProfileRepository;
import com.orbit.profile.domain.AccountId;
import com.orbit.profile.domain.PersonName;
import com.orbit.profile.domain.PhoneNumber;
import com.orbit.profile.domain.Profile;
import com.orbit.profile.domain.SignupStatus;
import com.orbit.support.TestcontainersConfiguration;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, ProfilePersistenceAdapter.class})
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("프로필 저장소")
class ProfilePersistenceAdapterTest {

    private static final AtomicLong ACCOUNT_IDS = new AtomicLong(1_000);

    @Autowired
    private ProfileRepository adapter;

    @Test
    @DisplayName("새 프로필을 저장하고 계정 식별자로 다시 찾는다")
    void savesAndFindsNewProfile() {
        AccountId accountId = newAccountId();

        adapter.save(Profile.start(accountId, new PersonName("홍길동"), new PhoneNumber("01012345678")));

        assertThat(adapter.findByAccountId(accountId)).hasValueSatisfying(found -> {
            assertThat(found.accountId()).isEqualTo(accountId);
            assertThat(found.name().value()).isEqualTo("홍길동");
            assertThat(found.phoneNumber().value()).isEqualTo("01012345678");
            assertThat(found.status()).isEqualTo(SignupStatus.PENDING_SIGNUP);
            assertThat(found.signedUpAt()).isEmpty();
        });
        assertThat(adapter.findByAccountId(newAccountId())).isEmpty();
    }

    @Test
    @DisplayName("저장된 프로필을 바꿔 다시 저장하면 바뀐 값을 찾는다")
    void updatesExistingProfile() {
        AccountId accountId = newAccountId();
        adapter.save(Profile.start(accountId, new PersonName("홍길동"), new PhoneNumber("01012345678")));
        Profile loaded = adapter.findByAccountId(accountId).orElseThrow();

        loaded.changeBasics(new PersonName("김철수"), new PhoneNumber("01087654321"));
        adapter.save(loaded);

        assertThat(adapter.findByAccountId(accountId)).hasValueSatisfying(found -> {
            assertThat(found.name().value()).isEqualTo("김철수");
            assertThat(found.phoneNumber().value()).isEqualTo("01087654321");
        });
    }

    @Test
    @DisplayName("다른 계정이 같은 연락처를 써도 저장한다")
    void allowsSamePhoneNumberAcrossAccounts() {
        AccountId first = newAccountId();
        AccountId second = newAccountId();

        adapter.save(Profile.start(first, new PersonName("홍길동"), new PhoneNumber("01055556666")));
        adapter.save(Profile.start(second, new PersonName("김철수"), new PhoneNumber("01055556666")));

        assertThat(adapter.findByAccountId(second)).isPresent();
    }

    private static AccountId newAccountId() {
        return new AccountId(ACCOUNT_IDS.incrementAndGet());
    }
}
