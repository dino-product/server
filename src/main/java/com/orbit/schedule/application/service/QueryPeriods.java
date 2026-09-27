package com.orbit.schedule.application.service;

import java.time.Duration;
import java.time.Instant;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.shared.error.BusinessException;

/** 기간으로 작업을 모아 보는 조회의 구간 입력 검증. 요청자 확인 뒤에 호출한다. 구간이 틀리면 400(SCHEDULE-003)이다. */
final class QueryPeriods {

    /** 한 번에 조회할 수 있는 가장 긴 구간. 한 달 화면을 넘지 않게 한다. */
    static final Duration MAX_PERIOD = Duration.ofDays(31);

    private QueryPeriods() {}

    /** from(포함)이 to(제외)보다 앞서고, 구간이 {@link #MAX_PERIOD} 이하인지 확인한다. */
    static void require(Instant from, Instant to) {
        if (from == null
                || to == null
                || !from.isBefore(to)
                || Duration.between(from, to).compareTo(MAX_PERIOD) > 0) {
            throw new BusinessException(ScheduleErrorCode.INVALID_WORK_INPUT);
        }
    }
}
