package com.orbit.schedule.adapter.in.web;

import java.time.Duration;
import java.time.Instant;

import com.orbit.schedule.application.port.in.query.dto.BacklogWorkInfo;
import com.orbit.schedule.domain.RejectionReason;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "대기함 작업 카드")
public record BacklogWorkResponse(
        @Schema(description = "작업 식별자", example = "10") Long workId,
        @Schema(description = "작업명", example = "인덕션 교체") String name,
        @Schema(description = "작업유형. 없으면 null") LabelResponse workType,
        @Schema(description = "등록자") StaffResponse registrar,

        @Schema(description = "등록 시각", example = "2026-10-18T01:00:00Z")
        Instant registeredAt,

        @Schema(description = "등록 때 미리 입력한 담당기사. 없으면 null") TechnicianResponse plannedTechnician,

        @Schema(description = "등록 때 미리 입력한 시작 시각. 없으면 null", example = "2026-10-21T01:00:00Z")
        Instant plannedStartTime,

        @Schema(description = "등록 때 미리 입력한 예상 소요시간(분). 없으면 null", example = "120")
        Integer plannedExpectedDurationMinutes,

        @Schema(description = "담당기사가 정해지지 않았는지", example = "true")
        boolean technicianMissing,

        @Schema(description = "시작 시각이나 예상 소요시간이 정해지지 않았는지", example = "true")
        boolean timeMissing,

        @Schema(description = "기사가 거절해 돌아온 작업인지", example = "false")
        boolean returnedByRejection,

        @Schema(
                description = "돌아오게 한 거절의 사유 코드(SCHEDULE_CONFLICT, ALREADY_ASSIGNED, LOCATION_TOO_FAR, "
                        + "SCOPE_MISMATCH, OTHER). 거절로 돌아온 작업이 아니면 null",
                example = "SCHEDULE_CONFLICT")
        RejectionReason rejectionReason,

        @Schema(description = "거절 메모(기타 사유). 없으면 null") String rejectionNote,

        @Schema(description = "대기함에 들어온 시각(등록 또는 마지막으로 돌아온 시각)", example = "2026-10-18T01:00:00Z")
        Instant backlogEnteredAt,

        @Schema(description = "대기함에서 방치된 작업인지(backlogEnteredAt부터 72시간 이상)", example = "false")
        boolean stale) {

    public static BacklogWorkResponse from(BacklogWorkInfo info) {
        Duration planned = info.plannedExpectedDuration();
        return new BacklogWorkResponse(
                info.workId(),
                info.name(),
                LabelResponse.from(info.workType()),
                StaffResponse.from(info.registrar()),
                info.registeredAt(),
                TechnicianResponse.from(info.plannedTechnician()),
                info.plannedStartTime(),
                planned == null ? null : Math.toIntExact(planned.toMinutes()),
                info.technicianMissing(),
                info.timeMissing(),
                info.returnedByRejection(),
                info.rejectionReason(),
                info.rejectionNote(),
                info.backlogEnteredAt(),
                info.stale());
    }
}
