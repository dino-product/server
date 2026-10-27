package com.orbit.notification.domain;

/** 알림을 보내는 채널. 지금은 기사 앱 푸시뿐이며, 총관리자·직원 알림은 웹 인앱(알림센터·헤더 배지)이라 발신 대기를 거치지 않는다. */
public enum DeliveryChannel {
    PUSH
}
