package com.orbit.organization.adapter.in.web;

import com.orbit.organization.application.port.in.query.dto.CompanyCheckInfo;
import com.orbit.organization.application.port.in.query.dto.ParticipationAvailability;

import io.swagger.v3.oas.annotations.media.Schema;

public record CompanyCheckResponse(
        @Schema(description = "발주사 식별자", example = "1") Long organizationId,

        @Schema(description = "발주사명", example = "오르빗 설비") String organizationName,

        @Schema(
                description = "참여 요청 가능 여부. AVAILABLE 요청 가능(비활성 소속만 있어도 요청 가능, 승인되면 그 소속이 다시 활성), "
                        + "ALREADY_MEMBER 이미 참여 중, PENDING_REQUEST 승인 대기 중인 요청 있음",
                example = "AVAILABLE")
        ParticipationAvailability availability,

        @Schema(description = "승인 대기 중인 요청 식별자. availability가 PENDING_REQUEST일 때만 있음", example = "3")
        Long pendingRequestId) {

    static CompanyCheckResponse from(CompanyCheckInfo info) {
        return new CompanyCheckResponse(
                info.organizationId(), info.organizationName(), info.availability(), info.pendingRequestId());
    }
}
