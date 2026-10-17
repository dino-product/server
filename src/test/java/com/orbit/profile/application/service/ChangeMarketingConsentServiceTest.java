package com.orbit.profile.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.profile.application.error.ProfileErrorCode;
import com.orbit.profile.application.port.in.command.dto.ChangeMarketingConsentCommand;
import com.orbit.profile.application.port.out.ConcurrentProfileUpdateException;
import com.orbit.profile.application.port.out.CurrentTermsPort;
import com.orbit.profile.application.port.out.ProfileRepository;
import com.orbit.profile.domain.AccountId;
import com.orbit.profile.domain.MarketingConsent;
import com.orbit.profile.domain.PersonName;
import com.orbit.profile.domain.PhoneNumber;
import com.orbit.profile.domain.Profile;
import com.orbit.profile.domain.TermsAgreement;
import com.orbit.profile.domain.TermsType;
import com.orbit.profile.domain.TermsVersions;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@DisplayName("마케팅 수신 동의 변경")
class ChangeMarketingConsentServiceTest {

    private static final Instant SIGNUP_AT = Instant.parse("2026-10-10T01:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-11T01:00:00Z");
    private static final TermsVersions CURRENT = new TermsVersions("s1", "p1", "m1");

    private ProfileRepository profiles;
    private ChangeMarketingConsentService service;

    @BeforeEach
    void setUp() {
        profiles = mock(ProfileRepository.class);
        CurrentTermsPort currentTerms = mock(CurrentTermsPort.class);
        when(currentTerms.currentVersions()).thenReturn(CURRENT);
        service = new ChangeMarketingConsentService(profiles, currentTerms, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("가입을 마친 계정이 켜면 마케팅 약관 시행 버전과 시각을 남기고, 끄면 변경 시각만 남겨 저장한다")
    void changesConsentOfSignedUpAccount() {
        Profile profile = signedUpProfile();
        when(profiles.findByAccountId(new AccountId(1L))).thenReturn(Optional.of(profile));

        service.change(new ChangeMarketingConsentCommand(1L, 1L, true));

        verify(profiles).save(profile);
        assertThat(profile.marketingConsent()).contains(new MarketingConsent(true, NOW));
        assertThat(profile.agreements()).contains(new TermsAgreement(TermsType.MARKETING, "m1", NOW));
    }

    @Test
    @DisplayName("필수 약관이 개정돼 재동의 전인 활성 계정도 마케팅 동의는 바꿀 수 있다")
    void changesConsentBeforeReconsent() {
        Profile profile = Profile.start(new AccountId(1L), new PersonName("홍길동"), new PhoneNumber("01012345678"));
        profile.agreeToTerms(new TermsVersions("s0", "p1", "m1"), SIGNUP_AT);
        when(profiles.findByAccountId(new AccountId(1L))).thenReturn(Optional.of(profile));

        service.change(new ChangeMarketingConsentCommand(1L, 1L, true));

        verify(profiles).save(profile);
        assertThat(profile.isUsable(CURRENT)).isFalse();
        assertThat(profile.marketingConsent()).contains(new MarketingConsent(true, NOW));
    }

    @Test
    @DisplayName("가입을 마치지 않았거나 프로필이 없으면 PROFILE-005로 거부하고 저장하지 않는다")
    void rejectsBeforeSignup() {
        when(profiles.findByAccountId(new AccountId(1L)))
                .thenReturn(Optional.of(
                        Profile.start(new AccountId(1L), new PersonName("홍길동"), new PhoneNumber("01012345678"))));
        when(profiles.findByAccountId(new AccountId(2L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.change(new ChangeMarketingConsentCommand(1L, 1L, true)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ProfileErrorCode.SIGNUP_NOT_COMPLETED));
        assertThatThrownBy(() -> service.change(new ChangeMarketingConsentCommand(2L, 2L, true)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ProfileErrorCode.SIGNUP_NOT_COMPLETED));
        verify(profiles, never()).save(any());
    }

    @Test
    @DisplayName("동의 값이 비면 COMMON-400, 다른 계정이면 PROFILE-002로 거부한다")
    void rejectsMissingValueAndOtherAccount() {
        assertThatThrownBy(() -> service.change(new ChangeMarketingConsentCommand(1L, 1L, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.change(new ChangeMarketingConsentCommand(1L, 2L, true)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ProfileErrorCode.PROFILE_NOT_FOUND));
        verify(profiles, never()).save(any());
    }

    @Test
    @DisplayName("다른 요청이 같은 프로필을 먼저 저장했으면 COMMON-409로 거부한다")
    void rejectsConcurrentUpdate() {
        when(profiles.findByAccountId(new AccountId(1L))).thenReturn(Optional.of(signedUpProfile()));
        doThrow(new ConcurrentProfileUpdateException(new AccountId(1L), new RuntimeException("stale")))
                .when(profiles)
                .save(any());

        assertThatThrownBy(() -> service.change(new ChangeMarketingConsentCommand(1L, 1L, true)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.CONFLICT));
    }

    private static Profile signedUpProfile() {
        Profile profile = Profile.start(new AccountId(1L), new PersonName("홍길동"), new PhoneNumber("01012345678"));
        profile.agreeToTerms(CURRENT, SIGNUP_AT);
        return profile;
    }
}
