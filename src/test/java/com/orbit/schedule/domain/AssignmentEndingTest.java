package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("배정 종료 기록")
class AssignmentEndingTest {

    private static final Instant ENDED_AT = Instant.parse("2026-09-20T02:00:00Z");
    private static final MembershipId MANAGER_ID = new MembershipId(99L);

    @Test
    @DisplayName("종료 시각·처리자·방식을 담는다")
    void holdsEndingDetails() {
        AssignmentEnding ending = new AssignmentEnding(ENDED_AT, MANAGER_ID, AssignmentEndReason.UNASSIGNED);

        assertThat(ending.endedAt()).isEqualTo(ENDED_AT);
        assertThat(ending.endedBy()).isEqualTo(MANAGER_ID);
        assertThat(ending.reason()).isEqualTo(AssignmentEndReason.UNASSIGNED);
    }

    @Test
    @DisplayName("종료 시각은 마이크로초로 자른다")
    void truncatesEndedAtToMicroseconds() {
        AssignmentEnding ending =
                new AssignmentEnding(ENDED_AT.plusNanos(1_999), MANAGER_ID, AssignmentEndReason.UNASSIGNED);

        assertThat(ending.endedAt()).isEqualTo(ENDED_AT.plusNanos(1_000));
    }

    @Test
    @DisplayName("종료 시각·처리자·방식 중 하나라도 없으면 거부한다")
    void requiresEveryValue() {
        assertThatThrownBy(() -> new AssignmentEnding(null, MANAGER_ID, AssignmentEndReason.UNASSIGNED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("endedAt must not be null");
        assertThatThrownBy(() -> new AssignmentEnding(ENDED_AT, null, AssignmentEndReason.UNASSIGNED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("endedBy must not be null");
        assertThatThrownBy(() -> new AssignmentEnding(ENDED_AT, MANAGER_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reason must not be null");
    }
}
