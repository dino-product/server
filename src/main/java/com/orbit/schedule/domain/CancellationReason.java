package com.orbit.schedule.domain;

/** 관리자가 작업을 취소할 때 고르는 객관식 사유. 기타({@link #OTHER})면 무엇 때문인지 메모를 함께 남긴다. */
public enum CancellationReason {
    CUSTOMER_REQUEST,
    TECHNICIAN_ISSUE,
    DUPLICATE_REGISTRATION,
    OTHER
}
