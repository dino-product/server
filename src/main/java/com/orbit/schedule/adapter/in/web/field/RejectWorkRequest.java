package com.orbit.schedule.adapter.in.web.field;

import com.orbit.schedule.domain.RejectionReason;

import io.swagger.v3.oas.annotations.media.Schema;

/** 배정 거절. 기타면 메모가 필수이고 다른 사유에는 메모를 받지 않는다. */
public record RejectWorkRequest(
        @Schema(
                description = "기사가 화면에서 본 배정의 순번(내 일정·작업 상세의 assignmentNumber). 그 뒤로 배정이 바뀌었으면 거절하지 않는다",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Integer assignmentNumber,

        @Schema(
                description = "거절 사유. SCHEDULE_CONFLICT(일정이 맞지 않음), ALREADY_ASSIGNED(이미 다른 작업이 있음), "
                        + "LOCATION_TOO_FAR(작업 위치가 너무 멂), SCOPE_MISMATCH(작업 내용이 맞지 않음), OTHER(기타)",
                example = "SCHEDULE_CONFLICT",
                requiredMode = Schema.RequiredMode.REQUIRED)
        RejectionReason reason,

        @Schema(description = "기타 사유 메모(OTHER일 때 필수, 최대 255자)", example = "차량 고장")
        String note) {}
