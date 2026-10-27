package com.orbit.profile.application.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.orbit.profile.WithdrawalBlock;
import com.orbit.profile.WithdrawalBlockReason;
import com.orbit.profile.WithdrawalBlocker;

/**
 * 등록된 모든 차단 판정 구현의 사유를 모아 사유마다 건수를 합치고 사유 순서대로 돌려준다. 탈퇴 가능 여부 확인과 탈퇴 직전 판정이 같은 결과를 내도록 함께
 * 쓴다.
 */
final class WithdrawalBlocks {

    private final List<WithdrawalBlocker> blockers;

    WithdrawalBlocks(List<WithdrawalBlocker> blockers) {
        this.blockers = List.copyOf(blockers);
    }

    List<WithdrawalBlock> of(Long accountId) {
        Map<WithdrawalBlockReason, Integer> counts = new EnumMap<>(WithdrawalBlockReason.class);
        blockers.stream()
                .flatMap(blocker -> blocker.blocksFor(accountId).stream())
                .forEach(block -> counts.merge(block.reason(), block.count(), Integer::sum));
        return counts.entrySet().stream()
                .map(entry -> new WithdrawalBlock(entry.getKey(), entry.getValue()))
                .toList();
    }
}
