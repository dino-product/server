package com.orbit.notification.application.port.out;

import com.orbit.notification.domain.Notification;

/**
 * 앱 푸시 요청. 저장한 알림(식별자 있음)만 넘기며, 호출은 알림을 저장하는 트랜잭션 안에서 일어난다. 구현은 그 자리에서 보내지 않고 같은 트랜잭션의 발신 대기열에
 * 넣어(커밋 실패 시 없는 알림의 푸시·외부 호출 동안의 커넥션 점유 방지), 커밋 뒤 디스패처가 {@link PushSender}로 보낸다. 기기 없음·토큰 만료·제공자 오류 같은
 * 발신 실패는 대기열이 기록하고 다시 보내므로 이 호출은 그런 실패를 던지지 않는다.
 */
public interface PushNotificationPort {

    void send(Notification notification);
}
