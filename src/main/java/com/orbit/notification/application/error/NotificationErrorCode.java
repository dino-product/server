package com.orbit.notification.application.error;

import org.springframework.http.HttpStatus;

import com.orbit.shared.error.BaseCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** notification 모듈 전용 오류. 코드 문자열은 HTTP 상태와 독립이며 중복·재사용하지 않는다. */
@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements BaseCode {
    INVALID_NOTIFICATION_INPUT(HttpStatus.BAD_REQUEST, "NOTIFICATION-001", "알림 조회 조건이 올바르지 않습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION-002", "알림을 찾을 수 없습니다."),
    NOT_NOTIFICATION_RECIPIENT(HttpStatus.FORBIDDEN, "NOTIFICATION-003", "본인에게 온 알림이 아닙니다."),
    NOTIFICATION_SETTING_BUSY(HttpStatus.CONFLICT, "NOTIFICATION-004", "알림 설정이 다른 요청으로 저장 중입니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
