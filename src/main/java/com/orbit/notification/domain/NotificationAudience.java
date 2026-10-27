package com.orbit.notification.domain;

/** 알림을 받는 쪽과 그 채널. */
public enum NotificationAudience {
    /** 담당 기사. 앱 푸시와 앱 알림센터로 받는다. */
    TECHNICIAN,
    /** 사무실(등록한 직원·총관리자). 웹 알림센터로만 받고 푸시는 없다. */
    OFFICE,
    /** 참여를 요청한 사용자. 그 발주사에 소속되기 전에도 앱 푸시와 앱 알림센터로 받는다. */
    APPLICANT
}
