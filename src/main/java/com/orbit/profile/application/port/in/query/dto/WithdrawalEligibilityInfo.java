package com.orbit.profile.application.port.in.query.dto;

import java.util.List;

import com.orbit.profile.WithdrawalBlock;

/** 막는 사유가 하나도 없을 때만 탈퇴할 수 있다. */
public record WithdrawalEligibilityInfo(boolean withdrawable, List<WithdrawalBlock> blocks) {

    public WithdrawalEligibilityInfo {
        blocks = List.copyOf(blocks);
    }
}
