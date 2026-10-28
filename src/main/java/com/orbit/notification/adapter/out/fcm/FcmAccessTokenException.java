package com.orbit.notification.adapter.out.fcm;

/** FCM 호출용 access token을 받지 못했다. 메시지에는 상태·오류 코드만 담고 비밀값은 담지 않는다. */
class FcmAccessTokenException extends RuntimeException {

    FcmAccessTokenException(String message) {
        super(message);
    }
}
