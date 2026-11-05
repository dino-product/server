package com.orbit.schedule.adapter.in.web.field;

import java.time.Instant;

import com.orbit.schedule.adapter.in.web.CompletionReportResponse;
import com.orbit.schedule.application.port.in.query.dto.WorkCompletionReportInfo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "작업의 완료보고")
public record WorkCompletionReportResponse(
        @Schema(description = "작업 식별자", example = "10") Long workId,

        @Schema(description = "보고를 제출한 담당 기사(기사 계약 식별자)", example = "3")
        Long technicianId,

        @Schema(description = "완료 시각", example = "2026-10-21T01:50:00Z")
        Instant completedAt,

        @Schema(description = "완료보고 내용") CompletionReportResponse report) {

    static WorkCompletionReportResponse from(WorkCompletionReportInfo info) {
        return new WorkCompletionReportResponse(
                info.workId(), info.technicianId(), info.completedAt(), CompletionReportResponse.from(info.report()));
    }
}
