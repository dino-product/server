package com.orbit.profile.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.profile.WithdrawalBlock;
import com.orbit.profile.WithdrawalBlocker;
import com.orbit.profile.application.port.in.query.CheckWithdrawalEligibilityUseCase;
import com.orbit.profile.application.port.in.query.dto.CheckWithdrawalEligibilityQuery;
import com.orbit.profile.application.port.in.query.dto.WithdrawalEligibilityInfo;
import com.orbit.profile.application.port.out.ProfileRepository;
import com.orbit.profile.domain.AccountId;

/**
 * 탈퇴 확인 화면 전에 탈퇴를 막는 조건이 있는지 알려 준다(기능명세 §7: 차단 조건 확인 → 차단 안내 또는 탈퇴 확인). 차단 판정 구현은 탈퇴를 막아야 할 상태를
 * 소유한 모듈이 둔다. 확인 뒤 상태가 바뀔 수 있으므로 탈퇴 요청 때 다시 판정한다.
 *
 * <p>조회지만 읽기 전용 트랜잭션으로 열지 않는다. {@link WithdrawalBlocker} 계약이 판정 구현에 행 잠금을 허용하는데(예: 발주사 행을 잠근 뒤 총관리자 수를
 * 센다), PostgreSQL은 읽기 전용 트랜잭션의 {@code SELECT ... FOR UPDATE}를 거부한다. 잠금은 응답과 함께 바로 풀린다.
 */
@Service
public class CheckWithdrawalEligibilityService implements CheckWithdrawalEligibilityUseCase {

    private final ProfileRepository profiles;
    private final WithdrawalBlocks withdrawalBlocks;

    public CheckWithdrawalEligibilityService(ProfileRepository profiles, List<WithdrawalBlocker> blockers) {
        this.profiles = profiles;
        this.withdrawalBlocks = new WithdrawalBlocks(blockers);
    }

    @Override
    @Transactional
    public WithdrawalEligibilityInfo check(CheckWithdrawalEligibilityQuery query) {
        AccountId accountId = ProfileOwnership.requireSelf(query.requesterAccountId(), query.accountId());
        profiles.findByAccountId(accountId).ifPresent(ProfileOwnership::requireNotWithdrawn);
        List<WithdrawalBlock> blocks = withdrawalBlocks.of(accountId.value());
        return new WithdrawalEligibilityInfo(blocks.isEmpty(), blocks);
    }
}
