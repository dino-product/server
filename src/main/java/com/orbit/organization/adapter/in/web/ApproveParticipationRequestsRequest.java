package com.orbit.organization.adapter.in.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.orbit.organization.domain.ParticipationType;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/** 중복·0 이하 식별자는 서비스가 거부한다. */
public record ApproveParticipationRequestsRequest(
        @ArraySchema(
                arraySchema =
                        @Schema(
                                description = "승인할 요청과 각 요청의 확정 유형. 한 건만 승인해도 목록으로 보낸다",
                                requiredMode = Schema.RequiredMode.REQUIRED),
                minItems = 1,
                maxItems = 100)
        @NotEmpty
        @Size(max = 100)
        List<@NotNull @Valid Approval> approvals) {

    public record Approval(
            @Schema(description = "참여 요청 식별자", example = "3", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            Long requestId,

            @Schema(
                    description = "확정 유형. 기본값은 요청의 희망 유형이며 다르게 정할 수 있다. STAFF 직원, TECHNICIAN 기사",
                    example = "TECHNICIAN",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            ParticipationType confirmedType) {}
}
