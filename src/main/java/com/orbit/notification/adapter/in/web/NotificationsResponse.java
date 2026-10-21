package com.orbit.notification.adapter.in.web;

import java.util.List;

import com.orbit.notification.application.port.in.query.dto.NotificationPageInfo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 목록 한 쪽")
public record NotificationsResponse(
        @Schema(description = "최신순 알림. 쪽이 끝을 넘으면 비어 있다") List<NotificationResponse> notifications,

        @Schema(description = "조건에 맞는 전체 알림 수", example = "15")
        long totalCount,

        @Schema(description = "적용한 쪽 번호(0부터)", example = "0")
        int page,

        @Schema(description = "적용한 한 쪽의 알림 수", example = "20")
        int size) {

    static NotificationsResponse from(NotificationPageInfo info) {
        return new NotificationsResponse(
                info.notifications().stream().map(NotificationResponse::from).toList(),
                info.totalCount(),
                info.page(),
                info.size());
    }
}
