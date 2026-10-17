package com.orbit.schedule.adapter.in.web;

import com.orbit.schedule.application.port.in.query.dto.LabelInfo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "조직이 정한 유형(작업유형·기사유형·직원유형)")
public record LabelResponse(
        @Schema(description = "유형 식별자", example = "3") Long id,

        @Schema(description = "유형 이름. 찾을 수 없으면 null", example = "설치")
        String name,

        @Schema(description = "유형 색. 없거나 찾을 수 없으면 null", example = "#7C5CFF")
        String color) {

    public static LabelResponse from(LabelInfo info) {
        return info == null ? null : new LabelResponse(info.id(), info.name(), info.color());
    }
}
