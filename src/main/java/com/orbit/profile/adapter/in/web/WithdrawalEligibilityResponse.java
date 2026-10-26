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
                        .map(block -> new Block(block.reason().name(), block.count()))
                        .toList());
    }

    public record Block(
            @Schema(
                    description = "사유. SOLE_OWNER(어느 발주사에서든 유일한 총관리자), WORK_IN_PROGRESS(기사 소속에 작업중 작업 있음), "
                            + "WORK_PENDING_ACCEPT(기사 소속에 응답 전 배정됨 작업 있음). 사유마다 한 번만 온다",
                    example = "SOLE_OWNER",
                    allowableValues = {"SOLE_OWNER", "WORK_IN_PROGRESS", "WORK_PENDING_ACCEPT"})
            String reason,

            @Schema(description = "탈퇴하려면 먼저 정리해야 할 대상의 수. SOLE_OWNER는 발주사 수, 나머지는 모든 소속을 합친 작업 수", example = "1")
            int count) {}
}
