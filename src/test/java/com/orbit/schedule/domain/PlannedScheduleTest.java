package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("대기함 작업이 미리 입력해 둔 일정")
class PlannedScheduleTest {

    private static final TechnicianId TECHNICIAN = new TechnicianId(3L);
    private static final Instant TEN = Instant.parse("2026-09-25T01:00:00Z");

    @Test
    @DisplayName("담당기사·시작시각·소요시간 중 일부만 담고, 시각·소요시간은 마이크로초로 자른다")
    void holdsPartialValues() {
        PlannedSchedule technicianOnly = new PlannedSchedule(TECHNICIAN, null, null);
        PlannedSchedule timeOnly = new PlannedSchedule(null, TEN.plusNanos(1_999), Duration.ofNanos(2_000_999));

        assertThat(technicianOnly.technicianId()).isEqualTo(TECHNICIAN);
        assertThat(technicianOnly.startTime()).isNull();
        assertThat(timeOnly.startTime()).isEqualTo(TEN.plusNanos(1_000));
        assertThat(timeOnly.expectedDuration()).isEqualTo(Duration.ofNanos(2_000_000));
    }

    @Test
    @DisplayName("아무 값도 없거나 세 값이 모두 있으면 거부한다(모두 있으면 배정한다)")
    void rejectsEmptyOrComplete() {
        assertThatThrownBy(() -> new PlannedSchedule(null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("planned schedule must have at least one value");
        assertThatThrownBy(() -> new PlannedSchedule(TECHNICIAN, TEN, Duration.ofHours(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("complete schedule must be assigned instead of planned");
    }

    @Test
    @DisplayName("소요시간은 0 초과 24시간 이하이고, 시작시각과 함께 있으면 종료시각을 계산할 수 있어야 한다")
    void validatesDuration() {
        for (Duration duration : new Duration[] {Duration.ZERO, Duration.ofHours(-1), Duration.ofHours(25)}) {
            assertThatThrownBy(() -> new PlannedSchedule(null, null, duration))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new PlannedSchedule(null, Instant.MAX, Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new PlannedSchedule(null, null, Duration.ofHours(24)).expectedDuration())
                .isEqualTo(Duration.ofHours(24));
    }
}
