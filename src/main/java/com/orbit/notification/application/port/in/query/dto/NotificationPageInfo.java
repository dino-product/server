package com.orbit.notification.application.port.in.query.dto;

import java.util.List;

/**
 * 알림 목록 한 쪽.
 *
 * @param notifications 최신순 알림. 쪽이 끝을 넘으면 비어 있다
 * @param totalCount 조건에 맞는 전체 알림 수
 * @param page 적용한 쪽 번호
 * @param size 적용한 한 쪽의 알림 수
 */
public record NotificationPageInfo(List<NotificationInfo> notifications, long totalCount, int page, int size) {

    public NotificationPageInfo {
        notifications = List.copyOf(notifications);
    }
}
