package com.orbit.organization.adapter.in.web;

import java.time.Instant;
import java.util.List;

import com.orbit.organization.application.port.in.query.dto.PendingParticipationRequestInfo;
import com.orbit.organization.domain.ParticipationType;

import io.swagger.v3.oas.annotations.media.Schema;

public record PendingParticipationRequestsResponse(
        @Schema(description = "승인 대기 요청 건수", example = "2") int count,
        @Schema(description = "승인 대기 요청. 요청 일시가 오래된 순") List<Request> requests) {

    static PendingParticipationRequestsResponse from(List<PendingParticipationRequestInfo> requests) {
        return new PendingParticipationRequestsResponse(
                requests.size(), requests.stream().map(Request::from).toList());
    }

    public record Request(
            @Schema(description = "참여 요청 식별자", example = "3")
            Long requestId,

            @Schema(description = "신청자 계정 식별자", example = "42")
            Long accountId,

            @Schema(description = "희망 유형. 확정 유형의 기본값. STAFF 직원, TECHNICIAN 기사", example = "TECHNICIAN")
            ParticipationType desiredType,

            @Schema(description = "요청 일시(UTC)", example = "2026-10-10T00:00:00Z")
            Instant requestedAt) {

        static Request from(PendingParticipationRequestInfo info) {
            return new Request(info.requestId(), info.accountId(), info.desiredType(), info.requestedAt());
        }
    }
}
