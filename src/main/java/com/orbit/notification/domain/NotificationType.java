package com.orbit.notification.domain;

import java.util.Optional;

/** 알림 종류. 받는 쪽과, 기사 알림이면 알림 설정의 어느 분류를 따르는지 정한다. 분류가 없는 기사 알림은 설정과 무관하게 항상 푸시한다. */
public enum NotificationType {
    WORK_ASSIGNED(NotificationAudience.TECHNICIAN, NotificationCategory.WORK_ASSIGNMENT, false),
    WORK_RESCHEDULED(NotificationAudience.TECHNICIAN, NotificationCategory.SCHEDULE_CHANGE, true),
    WORK_REASSIGNED_AWAY(NotificationAudience.TECHNICIAN, NotificationCategory.SCHEDULE_CHANGE, true),
    WORK_CANCELLED(NotificationAudience.TECHNICIAN, NotificationCategory.WORK_CANCELLATION, false),
    COMPLETION_REPORT_REQUESTED(NotificationAudience.TECHNICIAN, null, false),
    WORK_ACCEPTED(NotificationAudience.OFFICE, null, false),
    WORK_REJECTED(NotificationAudience.OFFICE, null, false),
    COMPLETION_REPORT_SUBMITTED(NotificationAudience.OFFICE, null, false);

    private final NotificationAudience audience;
    private final NotificationCategory category;
    private final boolean onlyForAcceptedWork;

    NotificationType(NotificationAudience audience, NotificationCategory category, boolean onlyForAcceptedWork) {
        this.audience = audience;
        this.category = category;
        this.onlyForAcceptedWork = onlyForAcceptedWork;
    }

    public NotificationAudience audience() {
        return audience;
    }

    /** 기사 알림 설정에서 이 종류를 켜고 끄는 분류. 사무실 알림과 항상 발송하는 기사 알림은 비어 있다. */
    public Optional<NotificationCategory> category() {
        return Optional.ofNullable(category);
    }

    /** 앱 푸시 대상 종류인지. 기사 알림만 푸시하고 사무실 알림은 웹 알림센터에만 남긴다. */
    public boolean pushes() {
        return audience == NotificationAudience.TECHNICIAN;
    }

    /**
     * 받는 기사가 작업을 수락한 상태였을 때만 보내는 종류인지. 일정 변경과 다른 기사에게 넘어감은 이미 수락한 작업만 알리고, 수락 전 작업이 바뀐 것은 알리지
     * 않는다([알림] §4).
     */
    public boolean onlyForAcceptedWork() {
        return onlyForAcceptedWork;
    }
}
