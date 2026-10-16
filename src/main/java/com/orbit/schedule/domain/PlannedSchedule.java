package com.orbit.schedule.domain;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 대기함 작업이 등록 때 일부만 입력해 둔 담당기사·시작시각·예상소요시간. 셋 중 하나 이상이 있고 셋이 모두 있지는 않다(모두 있으면 배정한다). 대기함 카드에 보이고,
 * 나중에 모두 채워 배정하면 실제 일정({@link WorkSchedule})으로 대체된다. 값의 규칙은 일정과 같다: 시각·소요시간은 마이크로초로 자르고, 소요시간은 0 초과 24시간
 * 이하이며, 시작시각과 함께 있으면 종료시각을 계산할 수 있어야 한다.
 */
public record PlannedSchedule(TechnicianId technicianId, Instant startTime, Duration expectedDuration) {

    public PlannedSchedule {
        if (technicianId == null && startTime == null && expectedDuration == null) {
            throw new IllegalArgumentException("planned schedule must have at least one value");
        }
        if (technicianId != null && startTime != null && expectedDuration != null) {
            throw new IllegalArgumentException("complete schedule must be assigned instead of planned");
        }
        if (expectedDuration != null) {
            Duration storedDuration = expectedDuration.truncatedTo(ChronoUnit.MICROS);
            if (!storedDuration.isPositive()) {
                throw new IllegalArgumentException("expectedDuration must be positive");
            }
            if (storedDuration.compareTo(WorkSchedule.MAX_EXPECTED_DURATION) > 0) {
                throw new IllegalArgumentException(
                        "expectedDuration must not exceed " + WorkSchedule.MAX_EXPECTED_DURATION);
            }
            expectedDuration = storedDuration;
        }
        if (startTime != null) {
            startTime = startTime.truncatedTo(ChronoUnit.MICROS);
            if (expectedDuration != null) {
                try {
                    startTime.plus(expectedDuration);
                } catch (DateTimeException | ArithmeticException e) {
                    throw new IllegalArgumentException("endTime must be representable", e);
                }
            }
        }
    }
}
