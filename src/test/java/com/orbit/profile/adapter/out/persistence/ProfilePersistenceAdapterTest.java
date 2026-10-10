package com.orbit.profile.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.profile.application.port.out.ConcurrentProfileUpdateException;
import com.orbit.profile.application.port.out.ProfileRepository;
import com.orbit.profile.domain.AccountId;
import com.orbit.profile.domain.MarketingConsent;
import com.orbit.profile.domain.PersonName;
import com.orbit.profile.domain.PhoneNumber;
import com.orbit.profile.domain.Profile;
import com.orbit.profile.domain.SignupStatus;
import com.orbit.profile.domain.TermsAgreement;
import com.orbit.profile.domain.TermsType;
import com.orbit.profile.domain.TermsVersions;
import com.orbit.support.TestcontainersConfiguration;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, ProfilePersistenceAdapter.class})
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("프로필 저장소")
class ProfilePersistenceAdapterTest {

    private static final AtomicLong ACCOUNT_IDS = new AtomicLong(1_000);
    private static final TermsVersions V1 = new TermsVersions("s1", "p1", "m1");
    private static final TermsVersions PRIVACY_REVISED = new TermsVersions("s1", "p2", "m1");
    private static final Instant SIGNUP_AT = Instant.parse("2026-10-10T01:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-01T01:00:00.123456Z");

    @Autowired
    private ProfileRepository adapter;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("새 프로필을 저장하고 계정 식별자로 다시 찾는다")
    void savesAndFindsNewProfile() {
        AccountId accountId = newAccountId();

        adapter.save(Profile.start(accountId, new PersonName("홍길동"), new PhoneNumber("01012345678")));
        flushAndClear();

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
        flushAndClear();

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

    @Test
    @DisplayName("약관 동의 기록·가입일·마케팅 동의를 저장하고, 재동의는 기존 기록 뒤에 더한다")
    void persistsTermsAgreementsAndAppendsReconsent() {
        AccountId accountId = newAccountId();
        Profile profile = Profile.start(accountId, new PersonName("홍길동"), new PhoneNumber("01012345678"));
        profile.agreeToTerms(V1, SIGNUP_AT);
        profile.changeMarketingConsent(true, V1, SIGNUP_AT);
        adapter.save(profile);
        flushAndClear();

        Profile loaded = adapter.findByAccountId(accountId).orElseThrow();
        loaded.agreeToTerms(PRIVACY_REVISED, LATER);
        adapter.save(loaded);
        flushAndClear();

        assertThat(adapter.findByAccountId(accountId)).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(SignupStatus.ACTIVE);
            assertThat(found.signedUpAt()).contains(SIGNUP_AT);
            assertThat(found.marketingConsent()).contains(new MarketingConsent(true, SIGNUP_AT));
            assertThat(found.agreements())
                    .containsExactly(
                            new TermsAgreement(TermsType.SERVICE, "s1", SIGNUP_AT),
                            new TermsAgreement(TermsType.PRIVACY, "p1", SIGNUP_AT),
                            new TermsAgreement(TermsType.MARKETING, "m1", SIGNUP_AT),
                            new TermsAgreement(TermsType.PRIVACY, "p2", LATER));
            assertThat(found.isUsable(PRIVACY_REVISED)).isTrue();
        });
    }

    @Test
    @DisplayName("불러온 뒤 다른 요청이 먼저 저장한 프로필을 덮어쓰려 하면 동시 수정 예외로 거부한다")
    void rejectsStaleProfileUpdate() {
        AccountId accountId = newAccountId();
        adapter.save(Profile.start(accountId, new PersonName("홍길동"), new PhoneNumber("01012345678")));
        flushAndClear();
        Profile stale = adapter.findByAccountId(accountId).orElseThrow();
        entityManager
                .getEntityManager()
                .createNativeQuery("update profiles set version = version + 1 where account_id = :id")
                .setParameter("id", accountId.value())
                .executeUpdate();

        stale.agreeToTerms(V1, SIGNUP_AT);

        assertThatThrownBy(() -> adapter.save(stale)).isInstanceOf(ConcurrentProfileUpdateException.class);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static AccountId newAccountId() {
        return new AccountId(ACCOUNT_IDS.incrementAndGet());
    }
}
