package com.orbit.organization.adapter.in.web;

import com.orbit.organization.application.port.in.query.dto.WorkTypeDeletionImpactInfo;

import io.swagger.v3.oas.annotations.media.Schema;

public record WorkTypeDeletionImpactResponse(
        @Schema(description = "삭제하면 유형 없음이 될 작업 수(등록·배정됨·작업전). 배정됨·작업전 작업의 담당 기사에게는 알림이 간다", example = "2")
        long releasedCount,

        @Schema(description = "삭제해도 이 유형의 이름·색상을 그대로 유지할 작업 수(작업중·완료·취소)", example = "3")
        long keptCount) {

    static WorkTypeDeletionImpactResponse from(WorkTypeDeletionImpactInfo impact) {
        return new WorkTypeDeletionImpactResponse(impact.releasedCount(), impact.keptCount());
    }
}
