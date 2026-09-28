package com.orbit.schedule.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 배정이 관리자의 조치로 끝난 시각·처리자(조직 소속)·방식. 종료 시각은 저장소 정밀도인 마이크로초로 잘라 둔다. */
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
        endedAt = endedAt.truncatedTo(ChronoUnit.MICROS);
    }
}
