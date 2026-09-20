package com.orbit.schedule.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/** 배정 시 확정되는 담당기사·시작시간·예상소요시간 묶음과 그로부터 파생되는 종료시간. */
public record WorkSchedule(MembershipId technicianId, LocalDateTime startTime, Duration expectedDuration) {

    public WorkSchedule {
        if (technicianId == null) {
            throw new IllegalArgumentException("technicianId must not be null");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("startTime must not be null");
        }
        if (expectedDuration == null || expectedDuration.isZero() || expectedDuration.isNegative()) {
            throw new IllegalArgumentException("expectedDuration must be positive");
        }
    }

    public LocalDateTime endTime() {
        return startTime.plus(expectedDuration);
    }
}
