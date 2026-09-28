package com.orbit.schedule.domain;

/** 현재 담당기사에게 다시 재배정하려 할 때. 입력 형식 오류와 구분해, 재시도·중복 요청을 호출자가 알아볼 수 있게 한다. */
public final class SameTechnicianException extends IllegalArgumentException {

    public SameTechnicianException() {
        super("reassign requires a different technician");
    }
}
