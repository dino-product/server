package com.orbit.schedule.application.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.shared.error.BusinessException;

/**
 * 기간으로 작업을 모아 보는 조회의 UTC 반열림 구간 [from, to). 요청자 확인 뒤에 {@link #of}로 입력을 검증한다. 다른 기록 시각처럼 저장소 정밀도인 마이크로초로
 * 잘라 둔다.
 */
record QueryPeriod(Instant from, Instant to) {

    /** 한 번에 조회할 수 있는 가장 긴 구간. 한 달 화면을 넘지 않게 한다. */
    static final Duration MAX_LENGTH = Duration.ofDays(31);

    /** from이 to보다 앞서고 구간이 {@link #MAX_LENGTH} 이하인지 확인한다. 아니면 400(SCHEDULE-003). */
    static QueryPeriod of(Instant from, Instant to) {
        if (from == null || to == null) {
            throw invalidInput();
        }
        Instant storedFrom = from.truncatedTo(ChronoUnit.MICROS);
        Instant storedTo = to.truncatedTo(ChronoUnit.MICROS);
        if (!storedFrom.isBefore(storedTo)
                || Duration.between(storedFrom, storedTo).compareTo(MAX_LENGTH) > 0) {
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
