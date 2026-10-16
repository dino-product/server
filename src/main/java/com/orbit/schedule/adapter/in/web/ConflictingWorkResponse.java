package com.orbit.schedule.adapter.in.web;

import java.time.Instant;

import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 같은 기사의 일정과 겹친 작업. 배정 변경과 작업 등록 응답이 함께 쓴다. */
public record ConflictingWorkResponse(
        @Schema(description = "작업 식별자", example = "88") Long workId,
        @Schema(description = "작업명", example = "세탁기 설치") String name,

        @Schema(description = "시작 시각", example = "2026-10-21T04:00:00Z")
        Instant startTime,

        @Schema(description = "종료 시각", example = "2026-10-21T06:00:00Z")
        Instant endTime) {

    static ConflictingWorkResponse from(ScheduleChangeInfo.ConflictingWork conflict) {
        return new ConflictingWorkResponse(
                conflict.workId(), conflict.name(), conflict.startTime(), conflict.endTime());
    }
}
