package com.orbit.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("프로필")
class ProfileTest {

    private static final AccountId ACCOUNT = new AccountId(1L);

    @Test
    @DisplayName("프로필을 처음 입력하면 가입 미완료로 시작하고 다음 단계는 약관 동의다")
    void startsPendingSignupWithTermsAsNextStep() {
        Profile profile = Profile.start(ACCOUNT, new PersonName("홍길동"), new PhoneNumber("01012345678"));

        assertThat(profile.accountId()).isEqualTo(ACCOUNT);
        assertThat(profile.name()).isEqualTo(new PersonName("홍길동"));
        assertThat(profile.phoneNumber()).isEqualTo(new PhoneNumber("01012345678"));
        assertThat(profile.status()).isEqualTo(SignupStatus.PENDING_SIGNUP);
        assertThat(profile.signedUpAt()).isEmpty();
        assertThat(profile.nextStep()).isEqualTo(SignupStep.TERMS);
    }

    @Test
    @DisplayName("이름과 연락처를 다시 입력하면 바뀐 값으로 남고 가입 상태는 그대로다")
    void changesBasicsWithoutChangingStatus() {
        Profile profile = Profile.start(ACCOUNT, new PersonName("홍길동"), new PhoneNumber("01012345678"));

        profile.changeBasics(new PersonName("김철수"), new PhoneNumber("01087654321"));

        assertThat(profile.name()).isEqualTo(new PersonName("김철수"));
        assertThat(profile.phoneNumber()).isEqualTo(new PhoneNumber("01087654321"));
        assertThat(profile.status()).isEqualTo(SignupStatus.PENDING_SIGNUP);
    }
}
