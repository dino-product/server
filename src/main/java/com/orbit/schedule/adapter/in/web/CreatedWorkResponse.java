package com.orbit.schedule.adapter.in.web;

import java.util.List;

import com.orbit.schedule.application.port.in.command.dto.CreatedWorkInfo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 작업 등록 결과. 함께 배정하려던 일정이 겹치는데 확인하지 않았으면 작업을 만들지 않아 workId가 없다. */
public record CreatedWorkResponse(
        @Schema(description = "새 작업 식별자. applied=false면 만들지 않아 없음", example = "301")
        Long workId,

        @Schema(
                description = "반영 여부. false면 겹침 경고를 보여 주고 확인 뒤 schedule.conflictConfirmed=true로 다시 보낸다",
                example = "true")
        boolean applied,

        @Schema(description = "같은 기사의 겹친 작업(시작시각 순)") List<ScheduleChangeResponse.ConflictingWork> conflicts) {

    static CreatedWorkResponse from(CreatedWorkInfo info) {
        ScheduleChangeResponse change = ScheduleChangeResponse.from(info.scheduleChange());
        return new CreatedWorkResponse(info.workId(), change.applied(), change.conflicts());
    }
}
