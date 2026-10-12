package com.orbit.profile.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.auth.AccountClosure;
import com.orbit.profile.AccountWithdrawn;
import com.orbit.profile.WithdrawalBlocker;
import com.orbit.profile.application.error.ProfileErrorCode;
import com.orbit.profile.application.port.in.command.WithdrawUseCase;
import com.orbit.profile.application.port.in.command.dto.WithdrawCommand;
import com.orbit.profile.application.port.out.ConcurrentProfileUpdateException;
import com.orbit.profile.application.port.out.ProfileRepository;
import com.orbit.profile.domain.AccountId;
import com.orbit.profile.domain.Profile;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

/**
 * 본인 계정을 즉시 탈퇴한다([조직·계정] 정책 §5.1, 기능명세 §6·§7). 프로필을 탈퇴 상태로 저장한 뒤 같은 트랜잭션에서 auth 계정 폐쇄 계약을 불러, 둘 중
 * 하나가 실패하면 함께 롤백된다. auth가 profile을 구독하면 의존이 순환하므로 profile이 auth를 직접 부른다. 프로필 행을 먼저 저장하고 계정 행을 잠가 잠금
 * 순서를 profile → auth로 고정한다. 탈퇴를 막는 조건은 확인 화면 뒤에 바뀔 수 있으므로 저장 직전에 다시 판정해 막히면 PROFILE-006으로 거부한다.
 *
 * <p>탈퇴 사실 {@link AccountWithdrawn}은 같은 트랜잭션에서 발행해 구독자가 커밋 뒤에만 받게 한다. 이미 탈퇴한 계정이면 프로필은 그대로 두고 계정 폐쇄만
 * 다시 확정하며 이벤트는 다시 내지 않는다. 탈퇴 직후 토큰 폐기가 확정되기 전에 같은 요청이 한 번 더 와도 성공으로 끝난다.
 */
@Service
public class WithdrawService implements WithdrawUseCase {

    private final ProfileRepository profiles;
    private final WithdrawalBlocks withdrawalBlocks;
    private final AccountClosure accountClosure;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public WithdrawService(
            ProfileRepository profiles,
            List<WithdrawalBlocker> blockers,
            AccountClosure accountClosure,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.profiles = profiles;
        this.withdrawalBlocks = new WithdrawalBlocks(blockers);
        this.accountClosure = accountClosure;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void withdraw(WithdrawCommand command) {
        AccountId accountId = ProfileOwnership.requireSelf(command.requesterAccountId(), command.accountId());
        Profile profile = profiles.findByAccountId(accountId).orElse(null);
        if (profile != null && profile.isWithdrawn()) {
            accountClosure.close(accountId.value());
            return;
        }
        if (!withdrawalBlocks.of(accountId.value()).isEmpty()) {
            throw new BusinessException(ProfileErrorCode.WITHDRAWAL_BLOCKED);
        }
        Instant now = clock.instant();
        Profile withdrawn;
        if (profile == null) {
            withdrawn = Profile.withdrawnWithoutProfile(accountId, now);
        } else {
            profile.withdraw(now);
            withdrawn = profile;
        }
        try {
            profiles.save(withdrawn);
        } catch (ConcurrentProfileUpdateException exception) {
            throw new BusinessException(CommonErrorCode.CONFLICT, exception);
        }
        accountClosure.close(accountId.value());
        eventPublisher.publishEvent(new AccountWithdrawn(accountId.value(), now));
    }
}
