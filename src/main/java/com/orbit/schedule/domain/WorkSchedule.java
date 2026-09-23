package com.orbit.schedule.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * 배정 시 확정되는 담당기사·시작시각·예상소요시간 묶음과 그로부터 파생되는 종료시각. 시각은 UTC {@link Instant}로 다루고, 화면의 날짜·시간 표시는 조직의 현지
 * 시간대로 변환한다.
 */
public record WorkSchedule(MembershipId technicianId, Instant startTime, Duration expectedDuration) {

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

    public Instant endTime() {
        return startTime.plus(expectedDuration);
    }
}
