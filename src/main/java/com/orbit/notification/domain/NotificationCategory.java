package com.orbit.notification.domain;

/** 기사가 알림 설정에서 켜고 끌 수 있는 알림 분류. 끈 분류는 푸시만 보내지 않고 알림센터에는 그대로 남긴다. */
public enum NotificationCategory {
    WORK_ASSIGNMENT,
    SCHEDULE_CHANGE,
    WORK_CANCELLATION
}
