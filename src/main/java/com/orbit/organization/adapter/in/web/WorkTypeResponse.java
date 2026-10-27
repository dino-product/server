package com.orbit.organization.adapter.in.web;

import com.orbit.organization.application.port.in.query.dto.WorkTypeInfo;

import io.swagger.v3.oas.annotations.media.Schema;

public record WorkTypeResponse(
        @Schema(description = "작업 유형 식별자. 작업 등록·수정의 작업 유형 식별자로 쓴다", example = "1")
        Long workTypeId,

        @Schema(description = "작업 유형 이름", example = "설치") String name,

        @Schema(description = "색상(대문자 #RRGGBB)", example = "#3B82F6")
        String color) {

    static WorkTypeResponse from(WorkTypeInfo workType) {
        return new WorkTypeResponse(workType.workTypeId(), workType.name(), workType.color());
    }
}
