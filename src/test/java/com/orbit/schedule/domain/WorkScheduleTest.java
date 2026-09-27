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
    @DisplayName("예상소요시간은 24시간까지 허용하고 넘으면 거부한다")
    void limitsExpectedDurationToOneDay() {
        assertThat(new WorkSchedule(TECHNICIAN_ID, START_TIME, Duration.ofHours(24)).expectedDuration())
                .isEqualTo(WorkSchedule.MAX_EXPECTED_DURATION);
        assertThatThrownBy(() -> new WorkSchedule(
                        TECHNICIAN_ID, START_TIME, Duration.ofHours(24).plusNanos(1_000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expectedDuration must not exceed PT24H");
    }

    @Test
    @DisplayName("시작시각·소요시간은 마이크로초로 잘라, 그보다 작은 단위만 다른 일정은 같은 일정이다")
    void truncatesToMicroseconds() {
        WorkSchedule withNanos = new WorkSchedule(
                TECHNICIAN_ID, START_TIME.plusNanos(1_999), Duration.ofHours(1).plusNanos(999));

        assertThat(withNanos.startTime()).isEqualTo(START_TIME.plusNanos(1_000));
        assertThat(withNanos.expectedDuration()).isEqualTo(Duration.ofHours(1));
        assertThat(withNanos)
                .isEqualTo(new WorkSchedule(TECHNICIAN_ID, START_TIME.plusNanos(1_000), Duration.ofHours(1)));
        assertThat(new WorkSchedule(
                                TECHNICIAN_ID, START_TIME, Duration.ofHours(24).plusNanos(999))
                        .expectedDuration())
                .isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("마이크로초로 자르면 0이 되는 소요시간은 양수가 아니라 거부한다")
    void rejectsDurationShorterThanMicrosecond() {
        assertThatThrownBy(() -> new WorkSchedule(TECHNICIAN_ID, START_TIME, Duration.ofNanos(999)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expectedDuration must be positive");
    }

    @Test
    @DisplayName("종료시간을 계산할 수 없는 일정은 거부해 이후 겹침 판정을 깨뜨리지 않는다")
    void rejectsUnrepresentableEndTime() {
        assertThatThrownBy(() -> new WorkSchedule(TECHNICIAN_ID, Instant.MAX.minusSeconds(60), Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("endTime must be representable");
    }

    @Test
    @DisplayName("지난 시각에 시작하는 일정도 사후 기록으로 허용한다")
    void allowsPastStartTime() {
        Instant past = Instant.parse("2020-01-01T00:00:00Z");

        assertThat(new WorkSchedule(TECHNICIAN_ID, past, Duration.ofHours(1)).startTime())
                .isEqualTo(past);
    }

    @Test
    @DisplayName("담당기사 없이 시간 입력만 먼저 검증할 수 있다")
    void validatesTimeWithoutTechnician() {
        WorkSchedule.requireValidTime(START_TIME, Duration.ofHours(1));

        assertThatThrownBy(() -> WorkSchedule.requireValidTime(null, Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("startTime must not be null");
        assertThatThrownBy(() -> WorkSchedule.requireValidTime(START_TIME, Duration.ZERO))
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
