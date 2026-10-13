package com.orbit.organization.adapter.in.web;

import java.time.Instant;

import com.orbit.organization.application.port.in.command.dto.ParticipationRequestInfo;
import com.orbit.organization.domain.ParticipationRequestStatus;
import com.orbit.organization.domain.ParticipationType;

import io.swagger.v3.oas.annotations.media.Schema;

public record ParticipationRequestResponse(
        @Schema(description = "참여 요청 식별자", example = "3") Long requestId,

        @Schema(description = "발주사 식별자", example = "1") Long organizationId,

        @Schema(description = "발주사명", example = "오르빗 설비") String organizationName,

        @Schema(description = "희망 유형. STAFF 직원, TECHNICIAN 기사", example = "TECHNICIAN")
        ParticipationType desiredType,

        @Schema(description = "요청 상태. PENDING 승인 대기, APPROVED 승인, REJECTED 거절, CANCELED 요청 취소", example = "PENDING")
        ParticipationRequestStatus status,

        @Schema(description = "요청 일시(UTC)", example = "2026-10-10T00:00:00Z")
        Instant requestedAt) {

    static ParticipationRequestResponse from(ParticipationRequestInfo info) {
        return new ParticipationRequestResponse(
                info.requestId(),
                info.organizationId(),
                info.organizationName(),
                info.desiredType(),
                info.status(),
                info.requestedAt());
    }
}
