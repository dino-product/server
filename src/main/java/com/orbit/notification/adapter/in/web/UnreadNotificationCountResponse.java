package com.orbit.notification.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "안 읽은 알림 수")
public record UnreadNotificationCountResponse(
        @Schema(description = "알림 아이콘 배지에 보일 안 읽은 알림 수", example = "3")
        long count) {}
