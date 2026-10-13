package com.orbit.organization.adapter.in.web;

import java.util.List;

import com.orbit.organization.application.port.in.command.dto.ParticipationRequestDecisionInfo;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestDecisionOutcome;
import com.orbit.organization.domain.ParticipationRequestStatus;

import io.swagger.v3.oas.annotations.media.Schema;

public record ParticipationRequestDecisionsResponse(
        @Schema(description = "보낸 순서대로의 요청별 결과. 일부가 실패해도 나머지는 처리된다")
        List<Result> results) {

    static ParticipationRequestDecisionsResponse from(List<ParticipationRequestDecisionInfo> results) {
        return new ParticipationRequestDecisionsResponse(
                results.stream().map(Result::from).toList());
    }

    public record Result(
            @Schema(description = "참여 요청 식별자", example = "3")
            Long requestId,

            @Schema(
                    description = "처리 결과. APPROVED 승인, REJECTED 거절, NOT_FOUND 없거나 다른 발주사의 요청, "
                            + "NOT_PENDING 이미 승인·거절·취소된 요청(status로 현재 상태 안내), "
                            + "ALREADY_MEMBER 신청자가 이미 이 발주사의 소속·기사 계약을 가짐",
                    example = "APPROVED")
            ParticipationRequestDecisionOutcome outcome,

            @Schema(
                    description =
                            "처리 뒤 요청 상태. NOT_FOUND이면 없음. PENDING 승인 대기, APPROVED 승인, REJECTED 거절, " + "CANCELED 요청 취소",
                    example = "APPROVED")
            ParticipationRequestStatus status) {

        static Result from(ParticipationRequestDecisionInfo info) {
            return new Result(info.requestId(), info.outcome(), info.status());
        }
    }
}
