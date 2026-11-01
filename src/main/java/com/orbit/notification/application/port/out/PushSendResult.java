package com.orbit.notification.application.port.out;

/** 기기 하나로 보낸 푸시의 결과. 제공자 오류는 고치면 성공할 수 있는지로 나눈다. */
public sealed interface PushSendResult {

    /** 보냈다. */
    record Sent(String providerMessageId) implements PushSendResult {}

    /** 다시 보내면 성공할 수 있는 실패. 일시 오류뿐 아니라 제공자 인증·설정 오류도 고치면 성공하므로 여기에 든다. 횟수 제한 없이 다시 보낸다. */
    record RetryableFailure(String reason) implements PushSendResult {}

    /** 요청 형식 오류처럼 다시 보내도 성공할 수 없는 실패. */
    record PermanentFailure(String reason) implements PushSendResult {}

    /** 제공자가 이 기기 토큰을 더 받지 않는다(앱 삭제·토큰 만료 등). 이 기기로는 다시 보내지 않는다. */
    record Unregistered(String reason) implements PushSendResult {}
}
