package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("배정 이력")
class AssignmentHistoryTest {

    private static final WorkSchedule SCHEDULE =
            new WorkSchedule(new MembershipId(1L), Instant.parse("2026-09-21T01:00:00Z"), Duration.ofMinutes(90));

    @Test
    @DisplayName("일정이 null이면 거부한다")
    void rejectsNullSchedule() {
        assertThatThrownBy(() -> new AssignmentHistory(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("schedule must not be null");
    }

    @Test
    @DisplayName("생성 시 배정 결과는 대기 상태이다")
    void startsPending() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE);

        assertThat(history.schedule()).isSameAs(SCHEDULE);
        assertThat(history.result()).isEqualTo(AssignmentResult.PENDING);
        assertThat(history.rejectionReason()).isEmpty();
    }

    @Test
    @DisplayName("대기 중인 배정을 수락한다")
    void acceptsPendingAssignment() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE);

        history.accept();

        assertThat(history.result()).isEqualTo(AssignmentResult.ACCEPTED);
    }

    @Test
    @DisplayName("대기 중인 배정을 사유와 함께 거절한다")
    void rejectsPendingAssignment() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE);

        history.reject(RejectionReason.SCHEDULE_CONFLICT);

        assertThat(history.result()).isEqualTo(AssignmentResult.REJECTED);
        assertThat(history.rejectionReason()).contains(RejectionReason.SCHEDULE_CONFLICT);
    }

    @Test
    @DisplayName("거절 사유가 null이면 거부한다")
    void rejectsNullRejectionReason() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE);

        assertThatThrownBy(() -> history.reject(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rejectionReason must not be null");
    }

    @Test
    @DisplayName("대기 중인 배정을 다른 기사에게 재배정한다")
    void reassignsPendingAssignment() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE);

        history.reassign();

        assertThat(history.result()).isEqualTo(AssignmentResult.REASSIGNED);
    }

    @Test
    @DisplayName("수락된 배정은 다시 수락할 수 없다")
    void cannotAcceptAcceptedAssignment() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE);
        history.accept();

        assertThatThrownBy(history::accept)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot accept when result is ACCEPTED");
    }

    @Test
    @DisplayName("거절된 배정은 재배정할 수 없다")
    void cannotReassignRejectedAssignment() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE);
        history.reject(RejectionReason.OTHER);

        assertThatThrownBy(history::reassign)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot reassign when result is REJECTED");
    }

    @Test
    @DisplayName("재배정된 배정은 거절할 수 없다")
    void cannotRejectReassignedAssignment() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE);
        history.reassign();

        assertThatThrownBy(() -> history.reject(RejectionReason.OTHER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot reject when result is REASSIGNED");
    }
}
