package com.orbit.schedule.adapter.in.web;

import com.orbit.schedule.application.port.in.query.dto.StaffInfo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "직원 소속(총관리자·직원). 작업의 등록자 표기에 쓴다")
public record StaffResponse(
        @Schema(description = "소속 식별자", example = "5") Long membershipId,

        @Schema(description = "이름. 찾을 수 없으면 null", example = "박서준")
        String name,

        @Schema(description = "총관리자인지. 찾을 수 없으면 null", example = "false")
        Boolean owner,

        @Schema(description = "직원유형. 없거나 찾을 수 없으면 null") LabelResponse staffType) {

    public static StaffResponse from(StaffInfo info) {
        return new StaffResponse(info.membershipId(), info.name(), info.owner(), LabelResponse.from(info.staffType()));
    }
}
