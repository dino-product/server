package com.orbit.profile.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.profile.WithdrawalBlock;
import com.orbit.profile.WithdrawalBlocker;
import com.orbit.profile.application.error.ProfileErrorCode;
import com.orbit.profile.application.port.in.query.dto.CheckWithdrawalEligibilityQuery;
import com.orbit.profile.application.port.in.query.dto.WithdrawalEligibilityInfo;
import com.orbit.profile.application.port.out.ProfileRepository;
import com.orbit.profile.domain.AccountId;
import com.orbit.profile.domain.Profile;
import com.orbit.shared.error.BusinessException;

@DisplayName("탈퇴 가능 여부 확인")
class CheckWithdrawalEligibilityServiceTest {

    private final ProfileRepository profiles = mock(ProfileRepository.class);

    @Test
    @DisplayName("차단 판정 구현이 없거나 모두 막지 않으면 탈퇴할 수 있다")
    void withdrawableWithoutBlocks() {
        when(profiles.findByAccountId(new AccountId(7L))).thenReturn(Optional.empty());
        CheckWithdrawalEligibilityService service =
                new CheckWithdrawalEligibilityService(profiles, List.of(accountId -> List.of()));

        assertThat(service.check(new CheckWithdrawalEligibilityQuery(7L, 7L)))
                .isEqualTo(new WithdrawalEligibilityInfo(true, List.of()));
        assertThat(new CheckWithdrawalEligibilityService(profiles, List.of())
                        .check(new CheckWithdrawalEligibilityQuery(7L, 7L))
                        .withdrawable())
                .isTrue();
    }

    @Test
    @DisplayName("모든 차단 판정 구현의 사유를 모아 돌려주고 탈퇴할 수 없다고 알린다")
    void collectsBlocksFromEveryBlocker() {
        when(profiles.findByAccountId(new AccountId(7L))).thenReturn(Optional.empty());
        WithdrawalBlocker organization = accountId -> List.of(new WithdrawalBlock("SOLE_OWNER", 1));
        WithdrawalBlocker schedule = accountId -> List.of(new WithdrawalBlock("WORK_IN_PROGRESS", 2));
        CheckWithdrawalEligibilityService service =
                new CheckWithdrawalEligibilityService(profiles, List.of(organization, schedule));

        assertThat(service.check(new CheckWithdrawalEligibilityQuery(7L, 7L)))
                .isEqualTo(new WithdrawalEligibilityInfo(
                        false,
                        List.of(new WithdrawalBlock("SOLE_OWNER", 1), new WithdrawalBlock("WORK_IN_PROGRESS", 2))));
    }

    @Test
    @DisplayName("다른 계정이나 이미 탈퇴한 계정은 PROFILE-002다")
    void hidesOtherOrWithdrawnAccount() {
        when(profiles.findByAccountId(new AccountId(7L)))
                .thenReturn(Optional.of(Profile.withdrawnWithoutProfile(
                        new AccountId(7L), Instant.parse("2026-10-12T00:00:00Z"), null)));
        CheckWithdrawalEligibilityService service = new CheckWithdrawalEligibilityService(profiles, List.of());

        assertThatThrownBy(() -> service.check(new CheckWithdrawalEligibilityQuery(7L, 8L)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ProfileErrorCode.PROFILE_NOT_FOUND));
        assertThatThrownBy(() -> service.check(new CheckWithdrawalEligibilityQuery(7L, 7L)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ProfileErrorCode.PROFILE_NOT_FOUND));
    }
}
