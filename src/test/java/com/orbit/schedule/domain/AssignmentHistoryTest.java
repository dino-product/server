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
    private static final Instant ASSIGNED_AT = Instant.parse("2026-09-20T01:00:00Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-09-20T02:00:00Z");

    @Test
    @DisplayName("일정이 null이면 거부한다")
    void rejectsNullSchedule() {
        assertThatThrownBy(() -> new AssignmentHistory(null, ASSIGNED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("schedule must not be null");
    }

    @Test
    @DisplayName("배정 시각이 null이면 거부한다")
    void rejectsNullAssignedAt() {
        assertThatThrownBy(() -> new AssignmentHistory(SCHEDULE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("assignedAt must not be null");
    }

    @Test
    @DisplayName("생성 시 배정 결과는 대기 상태이고 배정 시각을 기록한다")
    void startsPending() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT);

        assertThat(history.schedule()).isSameAs(SCHEDULE);
        assertThat(history.assignedAt()).isEqualTo(ASSIGNED_AT);
        assertThat(history.result()).isEqualTo(AssignmentResult.PENDING);
        assertThat(history.decidedAt()).isEmpty();
        assertThat(history.rejectionReason()).isEmpty();
    }

    @Test
    @DisplayName("대기 중인 배정을 수락하고 응답 시각을 기록한다")
    void acceptsPendingAssignment() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT);

        history.accept(DECIDED_AT);

        assertThat(history.result()).isEqualTo(AssignmentResult.ACCEPTED);
        assertThat(history.decidedAt()).contains(DECIDED_AT);
    }

    @Test
    @DisplayName("대기 중인 배정을 사유와 함께 거절하고 응답 시각을 기록한다")
    void rejectsPendingAssignment() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT);

        history.reject(RejectionReason.SCHEDULE_CONFLICT, DECIDED_AT);

        assertThat(history.result()).isEqualTo(AssignmentResult.REJECTED);
        assertThat(history.rejectionReason()).contains(RejectionReason.SCHEDULE_CONFLICT);
        assertThat(history.decidedAt()).contains(DECIDED_AT);
    }

    @Test
    @DisplayName("거절 사유가 null이면 거부한다")
    void rejectsNullRejectionReason() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT);

        assertThatThrownBy(() -> history.reject(null, DECIDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rejectionReason must not be null");
    }

    @Test
    @DisplayName("응답 전에 재배정·해제되면 대기 중인 배정을 마감하고 마감 시각을 기록한다")
    void closesPendingAssignment() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT);

        history.reassign(DECIDED_AT);

        assertThat(history.result()).isEqualTo(AssignmentResult.REASSIGNED);
        assertThat(history.decidedAt()).contains(DECIDED_AT);
    }

    @Test
    @DisplayName("응답 시각이 null이면 거부한다")
    void rejectsNullDecidedAt() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT);

        assertThatThrownBy(() -> history.accept(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("decidedAt must not be null");
    }

    @Test
    @DisplayName("응답 시각이 배정 시각보다 앞서면 거부한다")
    void rejectsDecidedAtBeforeAssignedAt() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT);

        assertThatThrownBy(() -> history.accept(ASSIGNED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("decidedAt must not be before assignedAt");
    }

    @Test
    @DisplayName("응답 시각이 배정 시각과 같으면 허용한다")
    void acceptsDecidedAtEqualToAssignedAt() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT);

        history.accept(ASSIGNED_AT);

        assertThat(history.decidedAt()).contains(ASSIGNED_AT);
    }

    @Test
    @DisplayName("수락된 배정은 다시 수락할 수 없다")
    void cannotAcceptAcceptedAssignment() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT);
        history.accept(DECIDED_AT);

        assertThatThrownBy(() -> history.accept(DECIDED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot accept when result is ACCEPTED");
    }

    @Test
    @DisplayName("거절된 배정은 재배정할 수 없다")
    void cannotReassignRejectedAssignment() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT);
        history.reject(RejectionReason.OTHER, DECIDED_AT);

        assertThatThrownBy(() -> history.reassign(DECIDED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot reassign when result is REJECTED");
    }

    @Test
    @DisplayName("재배정된 배정은 거절할 수 없다")
    void cannotRejectReassignedAssignment() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT);
        history.reassign(DECIDED_AT);

        assertThatThrownBy(() -> history.reject(RejectionReason.OTHER, DECIDED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot reject when result is REASSIGNED");
    }

    @Test
    @DisplayName("저장된 거절 이력을 복원한다")
    void restoresRejectedHistory() {
        AssignmentHistory history = AssignmentHistory.restore(
                SCHEDULE, ASSIGNED_AT, AssignmentResult.REJECTED, RejectionReason.OTHER, DECIDED_AT);

        assertThat(history.result()).isEqualTo(AssignmentResult.REJECTED);
        assertThat(history.rejectionReason()).contains(RejectionReason.OTHER);
        assertThat(history.assignedAt()).isEqualTo(ASSIGNED_AT);
        assertThat(history.decidedAt()).contains(DECIDED_AT);
    }

    @Test
    @DisplayName("저장된 대기 이력을 복원한다")
    void restoresPendingHistory() {
        AssignmentHistory history =
                AssignmentHistory.restore(SCHEDULE, ASSIGNED_AT, AssignmentResult.PENDING, null, null);

        assertThat(history.result()).isEqualTo(AssignmentResult.PENDING);
        assertThat(history.decidedAt()).isEmpty();
    }

    @Test
    @DisplayName("저장된 응답 전 마감 이력을 복원한다")
    void restoresClosedHistory() {
        AssignmentHistory history =
                AssignmentHistory.restore(SCHEDULE, ASSIGNED_AT, AssignmentResult.REASSIGNED, null, DECIDED_AT);

        assertThat(history.result()).isEqualTo(AssignmentResult.REASSIGNED);
        assertThat(history.decidedAt()).contains(DECIDED_AT);
        assertThat(history.rejectionReason()).isEmpty();
    }

    @Test
    @DisplayName("거절 사유가 있는 대기 이력은 복원하지 않는다")
    void rejectsRestoringPendingWithReason() {
        assertThatThrownBy(() -> AssignmentHistory.restore(
                        SCHEDULE, ASSIGNED_AT, AssignmentResult.PENDING, RejectionReason.OTHER, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only REJECTED history can have rejectionReason");
    }

    @Test
    @DisplayName("거절 사유가 있는 응답 전 마감 이력은 복원하지 않는다")
    void rejectsRestoringClosedWithReason() {
        assertThatThrownBy(() -> AssignmentHistory.restore(
                        SCHEDULE, ASSIGNED_AT, AssignmentResult.REASSIGNED, RejectionReason.OTHER, DECIDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only REJECTED history can have rejectionReason");
    }

    @Test
    @DisplayName("응답 시각이 있는 대기 이력은 복원하지 않는다")
    void rejectsRestoringPendingWithDecidedAt() {
        assertThatThrownBy(() ->
                        AssignmentHistory.restore(SCHEDULE, ASSIGNED_AT, AssignmentResult.PENDING, null, DECIDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PENDING history must not have decidedAt");
    }

    @Test
    @DisplayName("응답 시각이 없는 확정 이력은 복원하지 않는다")
    void rejectsRestoringDecidedWithoutDecidedAt() {
        assertThatThrownBy(
                        () -> AssignmentHistory.restore(SCHEDULE, ASSIGNED_AT, AssignmentResult.ACCEPTED, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ACCEPTED history must have decidedAt");
    }

    @Test
    @DisplayName("거절 사유가 없는 거절 이력은 복원하지 않는다")
    void rejectsRestoringRejectedWithoutReason() {
        assertThatThrownBy(() ->
                        AssignmentHistory.restore(SCHEDULE, ASSIGNED_AT, AssignmentResult.REJECTED, null, DECIDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rejectionReason must not be null");
    }

    @Test
    @DisplayName("거절이 아닌 이력에 거절 사유가 있으면 복원하지 않는다")
    void rejectsRestoringReasonOnNonRejected() {
        assertThatThrownBy(() -> AssignmentHistory.restore(
                        SCHEDULE, ASSIGNED_AT, AssignmentResult.ACCEPTED, RejectionReason.OTHER, DECIDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only REJECTED history can have rejectionReason");
    }
}
