package com.orbit.notification.application.port.out;

/**
 * 기사 앱 푸시 발신. 발신 대기열 디스패처가 커밋된 알림을 보낼 때 부른다. 수신 계정의 등록 기기로 보내고 기기별 결과를 하나로 모아 돌려준다. 실제 제공자
 * 어댑터(notification 문서 N-21)가 생기기 전에는 {@code local}·{@code test}·{@code dev} 전용 임시 어댑터만 있다. 예외를 던지면 다시 보낼 실패로 본다.
 */
public interface PushSender {

    PushSendResult send(PushMessage message);
}
