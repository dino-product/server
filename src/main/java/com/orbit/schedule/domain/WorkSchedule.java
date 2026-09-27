package com.orbit.schedule.domain;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 배정 시 확정되는 담당기사·시작시각·예상소요시간 묶음과 그로부터 파생되는 종료시각. 시각은 UTC {@link Instant}로 다루고, 화면의 날짜·시간 표시는 조직의 현지
 * 시간대로 변환한다. 예상소요시간은 0보다 길고 {@link #MAX_EXPECTED_DURATION} 이하이며, 지난 시각에 시작하는 일정(사후 기록)도 허용한다. 시작시각과
 * 예상소요시간은 저장소(PostgreSQL)의 정밀도인 마이크로초로 잘라 두어, 저장 전후의 같은 일정이 같게 비교되게 한다.
 */
public record WorkSchedule(MembershipId technicianId, Instant startTime, Duration expectedDuration) {

    /** 한 작업의 예상소요시간 상한. 하루를 넘는 작업은 없다고 본다. */
    public static final Duration MAX_EXPECTED_DURATION = Duration.ofHours(24);

    public WorkSchedule {
        if (technicianId == null) {
            throw new IllegalArgumentException("technicianId must not be null");
        }
        requireValidTime(startTime, expectedDuration);
        startTime = startTime.truncatedTo(ChronoUnit.MICROS);
        expectedDuration = expectedDuration.truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * 시작시각·예상소요시간이 일정이 될 수 있는지 확인한다. 담당기사를 정하기 전에 시간 입력만 먼저 검증할 때 쓴다. 종료시각을 계산할 수 없는 값도 거부해, 저장된 일정이
     * 이후 겹침 판정을 깨뜨리지 않게 한다.
     */
    public static void requireValidTime(Instant startTime, Duration expectedDuration) {
        if (startTime == null) {
            throw new IllegalArgumentException("startTime must not be null");
        }
        if (expectedDuration == null) {
            throw new IllegalArgumentException("expectedDuration must be positive");
        }
        // 저장되는 값(마이크로초로 자른 값)으로 판정한다. 잘라서 0이 되는 소요시간도 양수가 아니다.
        Duration storedDuration = expectedDuration.truncatedTo(ChronoUnit.MICROS);
        if (!storedDuration.isPositive()) {
            throw new IllegalArgumentException("expectedDuration must be positive");
        }
        if (storedDuration.compareTo(MAX_EXPECTED_DURATION) > 0) {
            throw new IllegalArgumentException("expectedDuration must not exceed " + MAX_EXPECTED_DURATION);
        }
        try {
            startTime.truncatedTo(ChronoUnit.MICROS).plus(storedDuration);
        } catch (DateTimeException | ArithmeticException e) {
            throw new IllegalArgumentException("endTime must be representable", e);
        }
    }

    public Instant endTime() {
        return startTime.plus(expectedDuration);
    }
}
