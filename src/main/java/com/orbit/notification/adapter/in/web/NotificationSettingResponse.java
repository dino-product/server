package com.orbit.notification.adapter.in.web;

import com.orbit.notification.application.port.in.query.dto.NotificationSettingInfo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "분류별 알림 푸시 수신 여부. 끈 분류도 알림센터에는 남는다")
public record NotificationSettingResponse(
        @Schema(description = "작업 배정 알림 푸시 수신", example = "true")
        boolean workAssignmentEnabled,

        @Schema(description = "일정 변경 알림(시간 변경, 다른 기사에게 넘어감) 푸시 수신", example = "true")
        boolean scheduleChangeEnabled,

        @Schema(description = "작업 취소 알림 푸시 수신", example = "false")
        boolean workCancellationEnabled) {

    static NotificationSettingResponse from(NotificationSettingInfo info) {
        return new NotificationSettingResponse(
                info.workAssignmentEnabled(), info.scheduleChangeEnabled(), info.workCancellationEnabled());
    }
}
