package com.orbit.schedule.adapter.in.web;

import java.util.List;

import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 일정 변경 결과. 겹치는데 확인하지 않은 요청은 반영하지 않고(applied=false) 겹친 작업을 돌려준다. */
public record ScheduleChangeResponse(
        @Schema(description = "반영 여부. false면 겹침 경고를 보여 주고 확인 뒤 conflictConfirmed=true로 다시 보낸다", example = "true")
        boolean applied,

        @Schema(description = "같은 기사의 겹친 작업(시작시각 순). 반영했어도 겹쳤으면 담는다")
        List<ConflictingWorkResponse> conflicts) {

    static ScheduleChangeResponse from(ScheduleChangeInfo info) {
        return new ScheduleChangeResponse(
                info.applied(),
                info.conflicts().stream().map(ConflictingWorkResponse::from).toList());
    }
}
