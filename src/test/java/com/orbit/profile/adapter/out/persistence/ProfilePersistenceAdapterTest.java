package com.orbit.profile.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

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
import com.orbit.profile.domain.WithdrawalReason;
import com.orbit.support.PersistenceAdapterTestSupport;

@DisplayName("프로필 저장소")
class ProfilePersistenceAdapterTest extends PersistenceAdapterTestSupport {

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
            assertThat(found.name().orElseThrow().value()).isEqualTo("홍길동");
            assertThat(found.phoneNumber().orElseThrow().value()).isEqualTo("01012345678");
            assertThat(found.status()).isEqualTo(SignupStatus.PENDING_SIGNUP);
            assertThat(found.signedUpAt()).isEmpty();
        });
        assertThat(adapter.findByAccountId(newAccountId())).isEmpty();
    }

    @Test
    @DisplayName("탈퇴한 프로필은 이름·연락처 없이 탈퇴 시각과 가입일·약관 동의 이력을 남긴다")
    void savesWithdrawnProfile() {
        AccountId accountId = newAccountId();
        Profile profile = Profile.start(accountId, new PersonName("홍길동"), new PhoneNumber("01012345678"));
        profile.agreeToTerms(V1, SIGNUP_AT);
        adapter.save(profile);
        Profile loaded = adapter.findByAccountId(accountId).orElseThrow();

        loaded.withdraw(LATER, null);
        adapter.save(loaded);
        flushAndClear();

        assertThat(adapter.findByAccountId(accountId)).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(SignupStatus.WITHDRAWN);
            assertThat(found.withdrawnAt()).contains(LATER);
            assertThat(found.name()).isEmpty();
            assertThat(found.phoneNumber()).isEmpty();
            assertThat(found.signedUpAt()).contains(SIGNUP_AT);
            assertThat(found.agreements()).hasSize(2);
        });
    }

    @Test
    @DisplayName("탈퇴 사유를 함께 저장하고 다시 읽는다")
    void savesWithdrawalReason() {
        AccountId accountId = newAccountId();
        WithdrawalReason reason = WithdrawalReason.from("😀 더 이상 쓰지 않아요").orElseThrow();

        adapter.save(Profile.withdrawnWithoutProfile(accountId, LATER, reason));
        flushAndClear();

        assertThat(adapter.findByAccountId(accountId))
                .flatMap(Profile::withdrawalReason)
                .contains(reason);
    }

    @Test
    @DisplayName("프로필 없이 탈퇴한 계정은 탈퇴 표시만 저장한다")
    void savesWithdrawalWithoutProfile() {
        AccountId accountId = newAccountId();

        adapter.save(Profile.withdrawnWithoutProfile(accountId, LATER, null));
        flushAndClear();

        assertThat(adapter.findByAccountId(accountId)).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(SignupStatus.WITHDRAWN);
            assertThat(found.withdrawnAt()).contains(LATER);
            assertThat(found.name()).isEmpty();
            assertThat(found.agreements()).isEmpty();
        });
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
            assertThat(found.name().orElseThrow().value()).isEqualTo("김철수");
            assertThat(found.phoneNumber().orElseThrow().value()).isEqualTo("01087654321");
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
    @DisplayName("이름 조회는 가입을 마친 계정만 돌려주고 가입 미완료·없는 계정은 뺀다")
    void findsNamesOfActiveProfilesOnly() {
        AccountId active = newAccountId();
        AccountId pending = newAccountId();
        Profile signedUp = Profile.start(active, new PersonName("홍길동"), new PhoneNumber("01012345678"));
        signedUp.agreeToTerms(V1, SIGNUP_AT);
        adapter.save(signedUp);
        adapter.save(Profile.start(pending, new PersonName("김철수"), new PhoneNumber("01087654321")));
        flushAndClear();

        assertThat(adapter.findActiveNames(Set.of(active, pending, newAccountId())))
                .containsExactlyEntriesOf(Map.of(active, new PersonName("홍길동")));
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

    @Test
    @DisplayName("공통 검사가 요청마다 계정별로 읽는 약관 동의 이력에는 계정 식별자 인덱스가 있다")
    void indexesTermsAgreementsByAccount() {
        List<?> indexes = entityManager
                .getEntityManager()
                .createNativeQuery("select indexdef from pg_indexes where tablename = 'profile_terms_agreements'")
                .getResultList();

        assertThat(indexes)
                .anySatisfy(definition -> assertThat(definition.toString()).contains("(account_id)"));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static AccountId newAccountId() {
        return new AccountId(ACCOUNT_IDS.incrementAndGet());
    }
}
