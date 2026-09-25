package com.orbit.schedule.domain;

import java.time.Instant;

/** 배정이 관리자의 조치로 끝난 시각·처리자(조직 소속)·방식. */
public record AssignmentEnding(Instant endedAt, MembershipId endedBy, AssignmentEndReason reason) {

    public AssignmentEnding {
        if (endedAt == null) {
            throw new IllegalArgumentException("endedAt must not be null");
        }
        if (endedBy == null) {
            throw new IllegalArgumentException("endedBy must not be null");
        }
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
    }
}
