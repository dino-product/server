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
import com.orbit.profile.application.port.in.command.dto.AgreeToTermsCommand;
import com.orbit.profile.application.port.out.ConcurrentProfileUpdateException;
import com.orbit.profile.application.port.out.CurrentTermsPort;
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
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@DisplayName("약관 동의")
class AgreeToTermsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T01:00:00Z");
    private static final TermsVersions CURRENT = new TermsVersions("s1", "p1", "m1");

    private ProfileRepository profiles;
    private AgreeToTermsService service;

    @BeforeEach
    void setUp() {
        profiles = mock(ProfileRepository.class);
        CurrentTermsPort currentTerms = mock(CurrentTermsPort.class);
        when(currentTerms.currentVersions()).thenReturn(CURRENT);
        service = new AgreeToTermsService(profiles, currentTerms, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("필수 약관 두 가지에 동의하면 활성이 되고 마케팅 선택값까지 기록해 저장한다")
    void activatesWithMarketingChoice() {
        Profile profile = pendingProfile();
        when(profiles.findByAccountId(new AccountId(1L))).thenReturn(Optional.of(profile));

        service.agree(new AgreeToTermsCommand(1L, 1L, true, true, true));

        verify(profiles).save(profile);
        assertThat(profile.status()).isEqualTo(SignupStatus.ACTIVE);
        assertThat(profile.signedUpAt()).contains(NOW);
        assertThat(profile.marketingConsent()).contains(new MarketingConsent(true, NOW));
        assertThat(profile.agreements()).contains(new TermsAgreement(TermsType.MARKETING, "m1", NOW));
    }

    @Test
    @DisplayName("마케팅 값을 보내지 않으면 처음 가입은 동의하지 않은 것으로, 재동의는 기존 값을 그대로 둔다")
    void keepsMarketingWhenOmitted() {
        Profile profile = pendingProfile();
        when(profiles.findByAccountId(new AccountId(1L))).thenReturn(Optional.of(profile));

        service.agree(new AgreeToTermsCommand(1L, 1L, true, true, null));

        assertThat(profile.marketingConsent()).contains(new MarketingConsent(false, NOW));

        Profile active = pendingProfile();
        Instant signedUpAt = Instant.parse("2026-09-01T01:00:00Z");
        TermsVersions old = new TermsVersions("s0", "p1", "m1");
        active.agreeToTerms(old, signedUpAt);
        active.changeMarketingConsent(true, old, signedUpAt);
        when(profiles.findByAccountId(new AccountId(1L))).thenReturn(Optional.of(active));

        service.agree(new AgreeToTermsCommand(1L, 1L, true, true, null));

        assertThat(active.marketingConsent()).contains(new MarketingConsent(true, signedUpAt));
        assertThat(active.agreements()).contains(new TermsAgreement(TermsType.SERVICE, "s1", NOW));
        assertThat(active.signedUpAt()).contains(signedUpAt);
    }

    @Test
    @DisplayName("필수 약관 중 하나라도 동의하지 않으면 PROFILE-004로 거부하고 저장하지 않는다")
    void rejectsMissingRequiredTerms() {
        when(profiles.findByAccountId(new AccountId(1L))).thenReturn(Optional.of(pendingProfile()));

        assertThatThrownBy(() -> service.agree(new AgreeToTermsCommand(1L, 1L, true, false, true)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ProfileErrorCode.REQUIRED_TERMS_NOT_AGREED));
        verify(profiles, never()).save(any());
    }

    @Test
    @DisplayName("프로필을 입력하기 전이면 PROFILE-003으로 거부한다")
    void rejectsBeforeProfile() {
        when(profiles.findByAccountId(new AccountId(1L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.agree(new AgreeToTermsCommand(1L, 1L, true, true, false)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ProfileErrorCode.PROFILE_REQUIRED));
    }

    @Test
    @DisplayName("다른 계정이면 PROFILE-002로 거부한다")
    void rejectsOtherAccount() {
        assertThatThrownBy(() -> service.agree(new AgreeToTermsCommand(1L, 2L, true, true, false)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ProfileErrorCode.PROFILE_NOT_FOUND));
        verify(profiles, never()).save(any());
    }

    @Test
    @DisplayName("다른 요청이 같은 프로필을 먼저 저장했으면 덮어쓰지 않고 COMMON-409로 거부한다")
    void rejectsConcurrentUpdate() {
        when(profiles.findByAccountId(new AccountId(1L)))
                .thenReturn(Optional.of(
                        Profile.start(new AccountId(1L), new PersonName("홍길동"), new PhoneNumber("01012345678"))));
        doThrow(new ConcurrentProfileUpdateException(new AccountId(1L), new RuntimeException("stale")))
                .when(profiles)
                .save(any());

        assertThatThrownBy(() -> service.agree(new AgreeToTermsCommand(1L, 1L, true, true, false)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.CONFLICT));
    }

    private static Profile pendingProfile() {
        return Profile.start(new AccountId(1L), new PersonName("홍길동"), new PhoneNumber("01012345678"));
    }
}
