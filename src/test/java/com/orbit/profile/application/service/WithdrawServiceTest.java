package com.orbit.profile.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.auth.AccountClosure;
import com.orbit.profile.application.error.ProfileErrorCode;
import com.orbit.profile.application.port.in.command.dto.WithdrawCommand;
import com.orbit.profile.application.port.out.ConcurrentProfileUpdateException;
import com.orbit.profile.application.port.out.ProfileRepository;
import com.orbit.profile.domain.AccountId;
import com.orbit.profile.domain.PersonName;
import com.orbit.profile.domain.PhoneNumber;
import com.orbit.profile.domain.Profile;
import com.orbit.profile.domain.SignupStatus;
import com.orbit.profile.domain.TermsVersions;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("회원 탈퇴")
class WithdrawServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T01:00:00Z");
    private static final AccountId ACCOUNT_ID = new AccountId(7L);

    @Mock
    private ProfileRepository profiles;

    @Mock
    private AccountClosure accountClosure;

    private WithdrawService service;

    @BeforeEach
    void setUp() {
        service = new WithdrawService(profiles, accountClosure, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("프로필을 탈퇴 상태로 저장한 뒤 같은 트랜잭션에서 auth 계정을 닫는다")
    void withdrawsProfileThenClosesAccount() {
        Profile profile = Profile.start(ACCOUNT_ID, new PersonName("홍길동"), new PhoneNumber("01012345678"));
        profile.agreeToTerms(new TermsVersions("s1", "p1", "m1"), NOW.minusSeconds(60));
        when(profiles.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(profile));

        service.withdraw(new WithdrawCommand(7L, 7L));

        ArgumentCaptor<Profile> saved = ArgumentCaptor.forClass(Profile.class);
        InOrder order = inOrder(profiles, accountClosure);
        order.verify(profiles).save(saved.capture());
        order.verify(accountClosure).close(7L);
        assertThat(saved.getValue().status()).isEqualTo(SignupStatus.WITHDRAWN);
        assertThat(saved.getValue().withdrawnAt()).contains(NOW);
        assertThat(saved.getValue().name()).isEmpty();
    }

    @Test
    @DisplayName("프로필이 없는 가입 미완료 계정은 탈퇴 표시를 만든다")
    void marksWithdrawalWithoutProfile() {
        when(profiles.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.empty());

        service.withdraw(new WithdrawCommand(7L, 7L));

        ArgumentCaptor<Profile> saved = ArgumentCaptor.forClass(Profile.class);
        verify(profiles).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(SignupStatus.WITHDRAWN);
        verify(accountClosure).close(7L);
    }

    @Test
    @DisplayName("이미 탈퇴했으면 프로필은 그대로 두고 계정 폐쇄만 다시 확정한다")
    void reconfirmsClosureWhenAlreadyWithdrawn() {
        when(profiles.findByAccountId(ACCOUNT_ID))
                .thenReturn(Optional.of(Profile.withdrawnWithoutProfile(ACCOUNT_ID, NOW.minusSeconds(60))));

        service.withdraw(new WithdrawCommand(7L, 7L));

        verify(profiles, never()).save(any());
        verify(accountClosure).close(7L);
    }

    @Test
    @DisplayName("다른 계정은 탈퇴시키지 않고 PROFILE-002로 거부한다")
    void rejectsOtherAccount() {
        assertThatThrownBy(() -> service.withdraw(new WithdrawCommand(7L, 8L)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ProfileErrorCode.PROFILE_NOT_FOUND));
        verify(accountClosure, never()).close(anyLong());
    }

    @Test
    @DisplayName("같은 프로필을 다른 요청이 먼저 저장했으면 COMMON-409로 거부하고 계정을 닫지 않는다")
    void rejectsConcurrentUpdate() {
        when(profiles.findByAccountId(ACCOUNT_ID))
                .thenReturn(
                        Optional.of(Profile.start(ACCOUNT_ID, new PersonName("홍길동"), new PhoneNumber("01012345678"))));
        doThrow(new ConcurrentProfileUpdateException(ACCOUNT_ID, new RuntimeException()))
                .when(profiles)
                .save(any());

        assertThatThrownBy(() -> service.withdraw(new WithdrawCommand(7L, 7L)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.CONFLICT));
        verify(accountClosure, never()).close(anyLong());
    }
}
