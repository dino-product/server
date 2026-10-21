package com.orbit.organization.adapter.in.web;

import java.util.List;

import com.orbit.organization.application.port.in.query.dto.WorkTypeInfo;

import io.swagger.v3.oas.annotations.media.Schema;

public record WorkTypesResponse(
        @Schema(description = "삭제되지 않은 작업 유형. 만든 순서대로") List<WorkTypeResponse> workTypes) {

    static WorkTypesResponse from(List<WorkTypeInfo> workTypes) {
        return new WorkTypesResponse(
                workTypes.stream().map(WorkTypeResponse::from).toList());
    }
}
