package com.orbit.schedule.application.error;

import org.springframework.http.HttpStatus;

import com.orbit.shared.error.BaseCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** schedule 모듈 전용 오류. 코드 문자열은 HTTP 상태와 독립이며 중복·재사용하지 않는다(errors.md). */
@Getter
@RequiredArgsConstructor
public enum ScheduleErrorCode implements BaseCode {
    WORK_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE-001", "작업을 찾을 수 없습니다."),
    INVALID_WORK_STATE(HttpStatus.CONFLICT, "SCHEDULE-002", "작업의 현재 상태에서는 요청한 처리를 할 수 없습니다."),
    INVALID_WORK_INPUT(HttpStatus.BAD_REQUEST, "SCHEDULE-003", "작업 요청 값이 올바르지 않습니다."),
    NOT_ORGANIZATION_MEMBER(HttpStatus.FORBIDDEN, "SCHEDULE-004", "해당 조직의 활성 구성원이 아닙니다."),
    ACTION_NOT_ALLOWED(HttpStatus.FORBIDDEN, "SCHEDULE-005", "이 작업을 처리할 권한이 없습니다."),
    TECHNICIAN_SCHEDULE_BUSY(HttpStatus.CONFLICT, "SCHEDULE-006", "같은 기사의 일정이 다른 요청으로 변경 중입니다. 잠시 후 다시 시도해 주세요."),
    SAME_TECHNICIAN(HttpStatus.CONFLICT, "SCHEDULE-007", "이미 해당 기사에게 배정된 작업입니다."),
    SCHEDULE_UNCHANGED(HttpStatus.CONFLICT, "SCHEDULE-008", "이미 같은 일정입니다."),
    NOT_ASSIGNED_TECHNICIAN(HttpStatus.FORBIDDEN, "SCHEDULE-009", "이 배정의 담당 기사가 아닙니다."),
    ASSIGNMENT_CHANGED(HttpStatus.CONFLICT, "SCHEDULE-010", "배정이 바뀌었습니다. 최신 배정을 확인해 주세요."),
    TECHNICIAN_ALREADY_WORKING(HttpStatus.CONFLICT, "SCHEDULE-011", "이미 작업중인 다른 작업이 있습니다. 그 작업을 마친 뒤 시작해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
