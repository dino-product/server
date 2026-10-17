package com.orbit.schedule.adapter.in.web;

import com.orbit.schedule.application.port.in.query.dto.TechnicianInfo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기사(기사 계약)")
public record TechnicianResponse(
        @Schema(description = "기사 계약 식별자", example = "12") Long technicianId,

        @Schema(description = "기사 이름. 찾을 수 없으면 null", example = "김민수")
        String name,

        @Schema(description = "기사유형. 없거나 찾을 수 없으면 null") LabelResponse technicianType) {

    public static TechnicianResponse from(TechnicianInfo info) {
        return info == null
                ? null
                : new TechnicianResponse(info.technicianId(), info.name(), LabelResponse.from(info.technicianType()));
    }
}
