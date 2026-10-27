package com.orbit.realtime.application.error;

import org.springframework.http.HttpStatus;

import com.orbit.shared.error.BaseCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** realtime 모듈 전용 오류. 코드 문자열은 HTTP 상태와 독립이며 중복·재사용하지 않는다. */
@Getter
@RequiredArgsConstructor
public enum RealtimeErrorCode implements BaseCode {
    NOT_OFFICE_MEMBER(HttpStatus.FORBIDDEN, "REALTIME-001", "해당 발주사의 활성 직원만 실시간 갱신을 받을 수 있습니다."),
    CONNECTION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "REALTIME-002", "지금은 실시간 연결을 받을 수 없습니다. 잠시 후 다시 연결해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
