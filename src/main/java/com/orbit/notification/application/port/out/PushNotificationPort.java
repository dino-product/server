package com.orbit.notification.application.port.out;

import com.orbit.notification.domain.Notification;

/**
 * 앱 푸시 발송. 알림센터 기록은 발송 결과와 무관하므로 구현은 기기 없음·토큰 만료·제공자 오류 같은 발송 실패를 던지지 않고 스스로 기록한다. 저장한 알림(식별자
 * 있음)만 넘긴다. 호출은 알림을 저장하는 트랜잭션 안에서 일어나므로, 외부로 보내는 구현은 커밋 뒤에 보내야 한다(커밋 실패 시 없는 알림의 푸시·외부 호출 동안의
 * 커넥션 점유 방지).
 */
public interface PushNotificationPort {

    void send(Notification notification);
}
