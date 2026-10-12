package com.orbit.profile.application.service;

import java.util.List;

import com.orbit.profile.WithdrawalBlock;
import com.orbit.profile.WithdrawalBlocker;

/** 등록된 모든 차단 판정 구현의 사유를 모은다. 탈퇴 가능 여부 확인과 탈퇴 직전 판정이 같은 결과를 내도록 함께 쓴다. */
final class WithdrawalBlocks {

    private final List<WithdrawalBlocker> blockers;

    WithdrawalBlocks(List<WithdrawalBlocker> blockers) {
        this.blockers = List.copyOf(blockers);
    }

    List<WithdrawalBlock> of(Long accountId) {
        return blockers.stream()
                .flatMap(blocker -> blocker.blocksFor(accountId).stream())
                .toList();
    }
}
