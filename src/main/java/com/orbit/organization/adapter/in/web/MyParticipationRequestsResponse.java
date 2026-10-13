package com.orbit.organization.adapter.in.web;

import java.time.Instant;
import java.util.List;

import com.orbit.organization.application.port.in.query.dto.MyParticipationRequestInfo;
import com.orbit.organization.domain.ParticipationRequestStatus;
import com.orbit.organization.domain.ParticipationType;

import io.swagger.v3.oas.annotations.media.Schema;

public record MyParticipationRequestsResponse(
        @Schema(description = "요청자가 보낸 모든 참여 요청. 요청 일시순") List<Request> requests) {

    static MyParticipationRequestsResponse from(List<MyParticipationRequestInfo> requests) {
        return new MyParticipationRequestsResponse(
                requests.stream().map(Request::from).toList());
    }

    public record Request(
            @Schema(description = "참여 요청 식별자", example = "3")
            Long requestId,

            @Schema(description = "발주사 식별자", example = "1") Long organizationId,

            @Schema(description = "발주사명(현재 이름)", example = "오르빗 설비")
            String organizationName,

            @Schema(description = "희망 유형. STAFF 직원, TECHNICIAN 기사", example = "TECHNICIAN")
            ParticipationType desiredType,

            @Schema(description = "확정 유형. 승인된 요청에만 있음. 희망 유형과 다를 수 있음", example = "STAFF")
            ParticipationType confirmedType,

            @Schema(description = "요청 상태. PENDING 승인 대기, APPROVED 승인, REJECTED 거절, CANCELED 요청 취소", example = "PENDING")
            ParticipationRequestStatus status,

            @Schema(description = "거절 사유. 사유를 입력해 거절한 요청에만 있음", example = "이번 달 채용이 마감되었습니다")
            String rejectReason,

            @Schema(description = "요청 일시(UTC)", example = "2026-10-10T00:00:00Z")
            Instant requestedAt,

            @Schema(description = "승인·거절 일시(UTC). 승인·거절된 요청에만 있음", example = "2026-10-11T00:00:00Z")
            Instant decidedAt) {

        static Request from(MyParticipationRequestInfo info) {
            return new Request(
                    info.requestId(),
                    info.organizationId(),
                    info.organizationName(),
                    info.desiredType(),
                    info.confirmedType(),
                    info.status(),
                    info.rejectReason(),
                    info.requestedAt(),
                    info.decidedAt());
        }
    }
}
