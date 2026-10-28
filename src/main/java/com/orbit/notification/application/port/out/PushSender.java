package com.orbit.notification.application.port.out;

/**
 * 기사 앱 푸시를 기기 하나로 보낸다. 발신 대기열 디스패처가 커밋된 알림을 수신 계정의 등록 기기마다 부르고, 기기별 결과를 모아 발신 결과를 정한다. 구현은 다시 보내지
 * 않고 한 번만 시도한다(재시도는 대기열이 한다). 예외를 던지면 다시 보낼 실패로 본다. 기기 토큰은 로그에 남기지 않는다.
 */
public interface PushSender {

    PushSendResult send(PushMessage message, String deviceToken);
}
