package com.orbit.organization.adapter.in.web;

import java.util.List;

import com.orbit.organization.application.port.in.command.dto.ParticipantRoleChangeInfo;
import com.orbit.organization.domain.ParticipationType;

import io.swagger.v3.oas.annotations.media.Schema;

public record ParticipantRoleChangeResponse(
        @Schema(description = "바뀐 뒤 역할. STAFF 직원, TECHNICIAN 기사", example = "STAFF")
        ParticipationType role,

        @Schema(description = "바뀐 뒤 역할의 식별자. STAFF면 직원 소속, TECHNICIAN이면 기사 계약 식별자(참여 인원 목록의 memberId)", example = "3")
        Long memberId,

        @Schema(description = "기사를 직원으로 바꾸며 대기함으로 돌린 작업 식별자. 없거나 직원을 기사로 바꿨으면 빈 목록")
        List<Long> releasedWorkIds) {

    static ParticipantRoleChangeResponse from(ParticipantRoleChangeInfo info) {
        return new ParticipantRoleChangeResponse(info.type(), info.memberId(), info.releasedWorkIds());
    }
}
