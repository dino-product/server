package com.orbit.schedule.domain;

/** 기사가 배정을 거절할 때 선택하는 객관식 사유. */
public enum RejectionReason {
    SCHEDULE_CONFLICT,
    ALREADY_ASSIGNED,
    LOCATION_TOO_FAR,
    SCOPE_MISMATCH,
    OTHER
}
