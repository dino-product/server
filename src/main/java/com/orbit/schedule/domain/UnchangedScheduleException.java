package com.orbit.schedule.domain;

/** 현재와 같은 시간으로 일정을 바꾸려 할 때. 입력 형식 오류와 구분해, 재시도·중복 요청을 호출자가 알아볼 수 있게 한다. */
public final class UnchangedScheduleException extends IllegalArgumentException {

    public UnchangedScheduleException() {
        super("reschedule requires a different time");
    }
}
