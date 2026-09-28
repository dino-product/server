package com.orbit.schedule.domain;

/** 관리자의 조치로 배정이 현재 배정에서 물러난 방식. 기사의 거절은 응답이라 여기에 없다. */
public enum AssignmentEndReason {
    /** 다른 기사에게 넘겼다. */
    REASSIGNED,
    /** 같은 기사의 시간을 바꿨다. */
    RESCHEDULED,
    /** 대기함으로 돌렸다. */
    UNASSIGNED,
    /** 작업을 취소했다. */
    CANCELLED
}
