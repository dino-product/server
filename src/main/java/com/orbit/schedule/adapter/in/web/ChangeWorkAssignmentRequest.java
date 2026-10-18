package com.orbit.schedule.adapter.in.web;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** 배정 변경 본문. 담당기사·일정과 요청자가 본 작업 버전이다. 입력 규칙은 서비스·도메인이 한 번만 검사한다. */
public record ChangeWorkAssignmentRequest(
        @Schema(description = "담당기사(기사 계약) 식별자", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
        Long technicianId,

        @Schema(
                description = "예정 시작 시각(ISO-8601, UTC 또는 오프셋 포함)",
                example = "2026-10-21T01:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant startTime,

        @Schema(description = "예상 소요시간(분, 0 초과 24시간 이하)", example = "120", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer expectedDurationMinutes,

        @Schema(description = "일정 겹침 경고를 확인했는지. 비우면 false", example = "false")
        Boolean conflictConfirmed,

        @Schema(
                description =
                        "화면에서 본 작업 버전(타임테이블·대기함·작업 상세 응답의 version). 없거나 음수면 SCHEDULE-003, 지금 버전과 다르면 SCHEDULE-016",
                example = "3",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long expectedVersion) {}
