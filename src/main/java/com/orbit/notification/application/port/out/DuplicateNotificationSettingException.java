package com.orbit.notification.application.port.out;

/** 같은 계정의 알림 설정을 동시에 처음 만들어 계정당 한 행 제약을 어겼다. 이 예외 뒤 트랜잭션은 되돌려야 한다. */
public class DuplicateNotificationSettingException extends RuntimeException {

    public DuplicateNotificationSettingException(Throwable cause) {
        super("notification setting already exists for the account", cause);
    }
}
