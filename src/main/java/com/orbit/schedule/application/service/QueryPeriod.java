package com.orbit.schedule.application.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.shared.error.BusinessException;

/**
 * 기간으로 작업을 모아 보거나 거르는 조회의 UTC 반열림 구간 [from, to). 요청자 확인 뒤에 {@link #of}·{@link #ofAnyLength}로 입력을 검증한다. 다른 기록
 * 시각처럼 저장소 정밀도인 마이크로초로 잘라 두고, 저장소가 다룰 수 없는 먼 시각은 400으로 막는다.
 */
record QueryPeriod(Instant from, Instant to) {

    /** 모아 보는 화면(타임테이블 등)이 한 번에 조회할 수 있는 가장 긴 구간. 한 달 화면을 넘지 않게 한다. */
    static final Duration MAX_LENGTH = Duration.ofDays(31);

    /** 조회 구간에 받을 수 있는 가장 이른·늦은 시각. 저장소(PostgreSQL timestamptz)에 그대로 넘길 수 있는 범위 안이다. */
    static final Instant EARLIEST = Instant.parse("1970-01-01T00:00:00Z");

    static final Instant LATEST = Instant.parse("9999-12-31T23:59:59Z");

    /** from이 to보다 앞서고 구간이 {@link #MAX_LENGTH} 이하인지 확인한다. 아니면 400(SCHEDULE-003). */
    static QueryPeriod of(Instant from, Instant to) {
        QueryPeriod period = ofAnyLength(from, to);
        if (Duration.between(period.from, period.to).compareTo(MAX_LENGTH) > 0) {
            throw invalidInput();
        }
        return period;
    }

    /** 길이 제한 없이 from이 to보다 앞서는지 확인한다(검색 조건처럼 걸러서 쪽으로 나누는 조회). 아니면 400(SCHEDULE-003). */
    static QueryPeriod ofAnyLength(Instant from, Instant to) {
        if (from == null || to == null) {
            throw invalidInput();
        }
        Instant storedFrom = from.truncatedTo(ChronoUnit.MICROS);
        Instant storedTo = to.truncatedTo(ChronoUnit.MICROS);
        if (storedFrom.isBefore(EARLIEST) || storedTo.isAfter(LATEST) || !storedFrom.isBefore(storedTo)) {
            throw invalidInput();
        }
        return new QueryPeriod(storedFrom, storedTo);
    }

    /** 일정이 이 구간과 겹치는지. 한쪽 끝이 다른 쪽 시작과 같으면 겹치지 않는다. */
    boolean overlaps(WorkSchedule schedule) {
        return schedule.startTime().isBefore(to) && schedule.endTime().isAfter(from);
    }

    private static BusinessException invalidInput() {
        return new BusinessException(ScheduleErrorCode.INVALID_WORK_INPUT);
    }
}
