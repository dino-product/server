package com.orbit.profile.adapter.in.web;

import java.util.List;

import com.orbit.profile.application.port.in.query.dto.WithdrawalEligibilityInfo;

import io.swagger.v3.oas.annotations.media.Schema;

public record WithdrawalEligibilityResponse(
        @Schema(description = "지금 탈퇴할 수 있는지. 막는 사유가 하나라도 있으면 false", example = "true")
        boolean withdrawable,

        @Schema(description = "탈퇴를 막는 사유. 탈퇴할 수 있으면 빈 목록이다") List<Block> blocks) {

    static WithdrawalEligibilityResponse from(WithdrawalEligibilityInfo info) {
        return new WithdrawalEligibilityResponse(
                info.withdrawable(),
                info.blocks().stream()
                        .map(block -> new Block(block.reason(), block.count()))
                        .toList());
    }

    public record Block(
            @Schema(
                    description = "사유 코드. 유일한 총관리자(SOLE_OWNER), 기사 소속의 작업중·배정됨 작업 등 탈퇴를 막는 상태를 소유한 기능이 정한다",
                    example = "SOLE_OWNER")
            String reason,

            @Schema(description = "탈퇴하려면 먼저 정리해야 할 대상(발주사·작업)의 수", example = "1")
            int count) {}
}
