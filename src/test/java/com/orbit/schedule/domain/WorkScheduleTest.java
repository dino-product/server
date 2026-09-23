package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("작업 일정")
class WorkScheduleTest {

    private static final MembershipId TECHNICIAN_ID = new MembershipId(1L);
    private static final Instant START_TIME = Instant.parse("2026-09-20T01:00:00Z");

    @Test
    @DisplayName("담당기사가 null이면 거부한다")
    void rejectsNullTechnicianId() {
        assertThatThrownBy(() -> new WorkSchedule(null, START_TIME, Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("technicianId must not be null");
    }

    @Test
    @DisplayName("시작시간이 null이면 거부한다")
    void rejectsNullStartTime() {
        assertThatThrownBy(() -> new WorkSchedule(TECHNICIAN_ID, null, Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("startTime must not be null");
    }

    @Test
    @DisplayName("예상소요시간이 null이면 거부한다")
    void rejectsNullExpectedDuration() {
        assertThatThrownBy(() -> new WorkSchedule(TECHNICIAN_ID, START_TIME, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expectedDuration must be positive");
    }

    @Test
    @DisplayName("예상소요시간이 0이면 거부한다")
    void rejectsZeroExpectedDuration() {
        assertThatThrownBy(() -> new WorkSchedule(TECHNICIAN_ID, START_TIME, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expectedDuration must be positive");
    }

    @Test
    @DisplayName("예상소요시간이 음수이면 거부한다")
    void rejectsNegativeExpectedDuration() {
        assertThatThrownBy(() -> new WorkSchedule(TECHNICIAN_ID, START_TIME, Duration.ofMinutes(-30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expectedDuration must be positive");
    }

    @Test
    @DisplayName("담당기사와 시작시간과 양수 예상소요시간으로 생성된다")
    void createsWithValidValues() {
        Duration expectedDuration = Duration.ofMinutes(1);

        WorkSchedule schedule = new WorkSchedule(TECHNICIAN_ID, START_TIME, expectedDuration);

        assertThat(schedule.technicianId()).isEqualTo(TECHNICIAN_ID);
        assertThat(schedule.startTime()).isEqualTo(START_TIME);
        assertThat(schedule.expectedDuration()).isEqualTo(expectedDuration);
    }

    @Test
    @DisplayName("종료시간은 시작시간에 예상소요시간을 더한 값이다")
    void calculatesEndTime() {
        WorkSchedule schedule = new WorkSchedule(TECHNICIAN_ID, START_TIME, Duration.ofMinutes(90));

        assertThat(schedule.endTime()).isEqualTo(Instant.parse("2026-09-20T02:30:00Z"));
    }
}
