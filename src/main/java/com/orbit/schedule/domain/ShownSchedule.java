package com.orbit.schedule.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 작업 목록·출력의 담당기사·예정시간 칸에 보일 일정({@link Work#shownSchedule}). 지금 일정이거나, 대기함 작업이면 등록 때 미리 입력한 값이나 마지막
 * 배정의 일정이다. 미리 입력한 값은 일부만 있을 수 있어 세 값 모두 비어 있을 수 있다.
 *
 * @param technicianId 담당기사. 없으면 null
 * @param startTime 시작시각. 없으면 null
 * @param expectedDuration 예상소요시간. 없으면 null
 */
public record ShownSchedule(TechnicianId technicianId, Instant startTime, Duration expectedDuration) {

    static ShownSchedule of(WorkSchedule schedule) {
        return new ShownSchedule(schedule.technicianId(), schedule.startTime(), schedule.expectedDuration());
    }

    static ShownSchedule of(PlannedSchedule planned) {
        return new ShownSchedule(planned.technicianId(), planned.startTime(), planned.expectedDuration());
    }

    /** 종료시각(시작시각 + 예상소요시간). 둘 중 하나라도 없으면 비어 있다. */
    public Optional<Instant> endTime() {
        return startTime == null || expectedDuration == null
                ? Optional.empty()
                : Optional.of(startTime.plus(expectedDuration));
    }
}
