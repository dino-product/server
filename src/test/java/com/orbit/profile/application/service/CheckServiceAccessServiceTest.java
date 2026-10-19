package com.orbit.profile.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.profile.application.port.in.query.dto.CheckServiceAccessQuery;
import com.orbit.profile.application.port.out.CurrentTermsPort;
import com.orbit.profile.application.port.out.ProfileRepository;
import com.orbit.profile.domain.AccountId;
import com.orbit.profile.domain.PersonName;
import com.orbit.profile.domain.PhoneNumber;
import com.orbit.profile.domain.Profile;
import com.orbit.profile.domain.TermsVersions;

@ExtendWith(MockitoExtension.class)
@DisplayName("서비스 이용 가능 여부")
class CheckServiceAccessServiceTest {

    private static final TermsVersions CURRENT = new TermsVersions("s1", "p1", "m1");

    @Mock
    private ProfileRepository profiles;

    @Mock
    private CurrentTermsPort currentTerms;

    @InjectMocks
    private CheckServiceAccessService service;

    @Test
    @DisplayName("프로필이 없는 계정은 쓸 수 없다")
    void deniesAccountWithoutProfile() {
        when(profiles.findByAccountId(new AccountId(1L))).thenReturn(Optional.empty());

        assertThat(service.canUseService(new CheckServiceAccessQuery(1L))).isFalse();
    }

    @Test
    @DisplayName("약관 동의 전 계정은 쓸 수 없고, 현재 필수 약관에 동의한 활성 계정은 쓸 수 있다")
    void allowsOnlyActiveAccountWithCurrentTerms() {
        Profile profile = Profile.start(new AccountId(1L), new PersonName("홍길동"), new PhoneNumber("01012345678"));
        when(profiles.findByAccountId(new AccountId(1L))).thenReturn(Optional.of(profile));
        when(currentTerms.currentVersions()).thenReturn(CURRENT);

        assertThat(service.canUseService(new CheckServiceAccessQuery(1L))).isFalse();

        profile.agreeToTerms(CURRENT, Instant.parse("2026-10-10T01:00:00Z"));

        assertThat(service.canUseService(new CheckServiceAccessQuery(1L))).isTrue();
    }

    @Test
    @DisplayName("필수 약관이 개정돼 재동의하지 않은 활성 계정은 쓸 수 없다")
    void deniesActiveAccountBeforeReconsent() {
        Profile profile = Profile.start(new AccountId(1L), new PersonName("홍길동"), new PhoneNumber("01012345678"));
        profile.agreeToTerms(CURRENT, Instant.parse("2026-10-10T01:00:00Z"));
        when(profiles.findByAccountId(new AccountId(1L))).thenReturn(Optional.of(profile));
        when(currentTerms.currentVersions()).thenReturn(new TermsVersions("s2", "p1", "m1"));

        assertThat(service.canUseService(new CheckServiceAccessQuery(1L))).isFalse();
    }
}
