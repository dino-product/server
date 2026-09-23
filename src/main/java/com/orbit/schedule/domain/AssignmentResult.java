package com.orbit.schedule.domain;

/** 기사 배정 시도의 처리 결과. */
public enum AssignmentResult {
    PENDING,
    ACCEPTED,
    REJECTED,
    REASSIGNED
}
