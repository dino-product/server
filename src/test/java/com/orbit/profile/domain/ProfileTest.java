package com.orbit.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("프로필")
class ProfileTest {

    private static final AccountId ACCOUNT = new AccountId(1L);
    private static final TermsVersions V1 = new TermsVersions("s1", "p1", "m1");
    private static final TermsVersions SERVICE_REVISED = new TermsVersions("s2", "p1", "m1");
    private static final Instant SIGNUP_AT = Instant.parse("2026-10-10T01:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-01T01:00:00Z");

    @Test
    @DisplayName("프로필을 처음 입력하면 가입 미완료로 시작하고 다음 단계는 약관 동의다")
    void startsPendingSignupWithTermsAsNextStep() {
        Profile profile = newProfile();

        assertThat(profile.accountId()).isEqualTo(ACCOUNT);
        assertThat(profile.name()).isEqualTo(new PersonName("홍길동"));
        assertThat(profile.phoneNumber()).isEqualTo(new PhoneNumber("01012345678"));
        assertThat(profile.status()).isEqualTo(SignupStatus.PENDING_SIGNUP);
        assertThat(profile.signedUpAt()).isEmpty();
        assertThat(profile.marketingConsent()).isEmpty();
        assertThat(profile.nextStep(V1)).isEqualTo(SignupStep.TERMS);
        assertThat(profile.isUsable(V1)).isFalse();
    }

    @Test
    @DisplayName("이름과 연락처를 다시 입력하면 바뀐 값으로 남고 가입 상태는 그대로다")
    void changesBasicsWithoutChangingStatus() {
        Profile profile = newProfile();

        profile.changeBasics(new PersonName("김철수"), new PhoneNumber("01087654321"));

        assertThat(profile.name()).isEqualTo(new PersonName("김철수"));
        assertThat(profile.phoneNumber()).isEqualTo(new PhoneNumber("01087654321"));
        assertThat(profile.status()).isEqualTo(SignupStatus.PENDING_SIGNUP);
    }

    @Test
    @DisplayName("필수 약관에 동의하면 현재 버전과 시각을 남기고 활성이 되며 가입일은 그 시각이다. 마케팅은 동의하지 않은 상태로 시작한다")
    void activatesWhenRequiredTermsAgreed() {
        Profile profile = newProfile();

        profile.agreeToTerms(V1, SIGNUP_AT);

        assertThat(profile.status()).isEqualTo(SignupStatus.ACTIVE);
        assertThat(profile.signedUpAt()).contains(SIGNUP_AT);
        assertThat(profile.agreements())
                .containsExactly(
                        new TermsAgreement(TermsType.SERVICE, "s1", SIGNUP_AT),
                        new TermsAgreement(TermsType.PRIVACY, "p1", SIGNUP_AT));
        assertThat(profile.marketingConsent()).contains(new MarketingConsent(false, SIGNUP_AT));
        assertThat(profile.nextStep(V1)).isEqualTo(SignupStep.COMPLETED);
        assertThat(profile.isUsable(V1)).isTrue();
    }

    @Test
    @DisplayName("마케팅 수신에 동의하면 마케팅 약관 버전과 시각을 남기고, 끄면 바뀐 시각만 남긴다")
    void recordsMarketingConsentChanges() {
        Profile profile = newProfile();
        profile.agreeToTerms(V1, SIGNUP_AT);

        profile.changeMarketingConsent(true, V1, SIGNUP_AT);
        profile.changeMarketingConsent(false, V1, LATER);

        assertThat(profile.marketingConsent()).contains(new MarketingConsent(false, LATER));
        assertThat(profile.agreements()).contains(new TermsAgreement(TermsType.MARKETING, "m1", SIGNUP_AT));
        assertThat(profile.agreements()).hasSize(3);
    }

    @Test
    @DisplayName("마케팅 동의 값이 그대로면 변경 시각을 바꾸지 않는다")
    void keepsMarketingConsentWhenUnchanged() {
        Profile profile = newProfile();
        profile.agreeToTerms(V1, SIGNUP_AT);

        profile.changeMarketingConsent(false, V1, LATER);

        assertThat(profile.marketingConsent()).contains(new MarketingConsent(false, SIGNUP_AT));
    }

    @Test
    @DisplayName("가입을 마치기 전에는 마케팅 동의만 따로 바꿀 수 없다")
    void rejectsMarketingChangeBeforeSignup() {
        Profile profile = newProfile();

        assertThatThrownBy(() -> profile.changeMarketingConsent(true, V1, SIGNUP_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("필수 약관이 개정되면 활성 계정도 재동의 전까지 쓸 수 없고, 재동의하면 기록을 더하며 가입일은 그대로다")
    void requiresReconsentAfterRequiredTermsRevision() {
        Profile profile = newProfile();
        profile.agreeToTerms(V1, SIGNUP_AT);

        assertThat(profile.isUsable(SERVICE_REVISED)).isFalse();
        assertThat(profile.nextStep(SERVICE_REVISED)).isEqualTo(SignupStep.TERMS);

        profile.agreeToTerms(SERVICE_REVISED, LATER);

        assertThat(profile.isUsable(SERVICE_REVISED)).isTrue();
        assertThat(profile.signedUpAt()).contains(SIGNUP_AT);
        assertThat(profile.agreements())
                .containsExactly(
                        new TermsAgreement(TermsType.SERVICE, "s1", SIGNUP_AT),
                        new TermsAgreement(TermsType.PRIVACY, "p1", SIGNUP_AT),
                        new TermsAgreement(TermsType.SERVICE, "s2", LATER));
    }

    @Test
    @DisplayName("이미 현재 버전에 동의했으면 같은 동의를 다시 남기지 않는다")
    void doesNotDuplicateCurrentAgreements() {
        Profile profile = newProfile();
        profile.agreeToTerms(V1, SIGNUP_AT);

        profile.agreeToTerms(V1, LATER);

        assertThat(profile.agreements()).hasSize(2);
    }

    @Test
    @DisplayName("마케팅 약관만 개정되면 계속 쓸 수 있다")
    void staysUsableWhenOnlyMarketingTermsRevised() {
        Profile profile = newProfile();
        profile.agreeToTerms(V1, SIGNUP_AT);

        assertThat(profile.isUsable(new TermsVersions("s1", "p1", "m2"))).isTrue();
    }

    private static Profile newProfile() {
        return Profile.start(ACCOUNT, new PersonName("홍길동"), new PhoneNumber("01012345678"));
    }
}
