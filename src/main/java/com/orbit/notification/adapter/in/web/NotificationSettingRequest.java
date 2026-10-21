package com.orbit.notification.adapter.in.web;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** 알림 설정 저장 본문. 세 분류의 수신 여부를 모두 보낸다. */
public record NotificationSettingRequest(
        @Schema(description = "작업 배정 알림 푸시 수신", example = "true", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
        Boolean workAssignmentEnabled,

        @Schema(
                description = "일정 변경 알림(시간 변경, 다른 기사에게 넘어감) 푸시 수신",
                example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Boolean scheduleChangeEnabled,

        @Schema(description = "작업 취소 알림 푸시 수신", example = "false", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
        Boolean workCancellationEnabled) {}
