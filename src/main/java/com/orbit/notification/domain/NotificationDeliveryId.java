package com.orbit.notification.domain;

/** 알림 발신 대기 식별자. */
public record NotificationDeliveryId(Long value) {

    public NotificationDeliveryId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("notificationDeliveryId must be positive");
        }
    }
}
