package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("배정 이력")
class AssignmentHistoryTest {

    private static final MembershipId MANAGER_ID = new MembershipId(99L);
    private static final MembershipId OTHER_MANAGER_ID = new MembershipId(98L);

    private static final WorkSchedule SCHEDULE =
            new WorkSchedule(new MembershipId(1L), Instant.parse("2026-09-21T01:00:00Z"), Duration.ofMinutes(90));
    private static final Instant ASSIGNED_AT = Instant.parse("2026-09-20T01:00:00Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-09-20T02:00:00Z");

    @Test
    @DisplayName("일정이 null이면 거부한다")
    void rejectsNullSchedule() {
        assertThatThrownBy(() -> new AssignmentHistory(null, ASSIGNED_AT, MANAGER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("schedule must not be null");
    }

    @Test
    @DisplayName("배정 시각이 null이면 거부한다")
    void rejectsNullAssignedAt() {
        assertThatThrownBy(() -> new AssignmentHistory(SCHEDULE, null, MANAGER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("assignedAt must not be null");
    }

    @Test
    @DisplayName("생성 시 배정 결과는 대기 상태이고 배정 시각을 기록한다")
    void startsPending() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT, MANAGER_ID);

        assertThat(history.schedule()).isSameAs(SCHEDULE);
        assertThat(history.assignedAt()).isEqualTo(ASSIGNED_AT);
        assertThat(history.result()).isEqualTo(AssignmentResult.PENDING);
        assertThat(history.decidedAt()).isEmpty();
        assertThat(history.rejectionReason()).isEmpty();
    }

    @Test
    @DisplayName("대기 중인 배정을 수락하고 응답 시각을 기록한다")
    void acceptsPendingAssignment() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT, MANAGER_ID);

        history.accept(DECIDED_AT);

        assertThat(history.result()).isEqualTo(AssignmentResult.ACCEPTED);
        assertThat(history.decidedAt()).contains(DECIDED_AT);
    }

    @Test
    @DisplayName("대기 중인 배정을 사유와 함께 거절하고 응답 시각을 기록한다")
    void rejectsPendingAssignment() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT, MANAGER_ID);

        history.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), DECIDED_AT);

        assertThat(history.result()).isEqualTo(AssignmentResult.REJECTED);
        assertThat(history.rejectionReason()).contains(RejectionReason.SCHEDULE_CONFLICT);
        assertThat(history.decidedAt()).contains(DECIDED_AT);
    }

    @Test
    @DisplayName("거절 사유 없이 거절할 수 없다")
    void rejectsNullRejectionReason() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT, MANAGER_ID);

        assertThatThrownBy(() -> history.reject(null, DECIDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rejection must not be null");
    }

    @Test
    @DisplayName("응답 시각이 null이면 거부한다")
    void rejectsNullDecidedAt() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT, MANAGER_ID);

        assertThatThrownBy(() -> history.accept(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("decidedAt must not be null");
    }

    @Test
    @DisplayName("응답 시각이 배정 시각보다 앞서면 거부한다")
    void rejectsDecidedAtBeforeAssignedAt() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT, MANAGER_ID);

        assertThatThrownBy(() -> history.accept(ASSIGNED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("decidedAt must not be before assignedAt");
    }

    @Test
    @DisplayName("응답 시각이 배정 시각과 같으면 허용한다")
    void acceptsDecidedAtEqualToAssignedAt() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT, MANAGER_ID);

        history.accept(ASSIGNED_AT);

        assertThat(history.decidedAt()).contains(ASSIGNED_AT);
    }

    @Test
    @DisplayName("수락된 배정은 다시 수락할 수 없다")
    void cannotAcceptAcceptedAssignment() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT, MANAGER_ID);
        history.accept(DECIDED_AT);

        assertThatThrownBy(() -> history.accept(DECIDED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot accept when result is ACCEPTED");
    }

    @Test
    @DisplayName("거절된 배정은 기사의 응답으로 끝났으므로 관리자 조치로 다시 끝낼 수 없다")
    void cannotEndRejectedAssignment() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT, MANAGER_ID);
        history.reject(new Rejection(RejectionReason.OTHER, "기타 사유"), DECIDED_AT);

        assertThatThrownBy(
                        () -> history.end(new AssignmentEnding(DECIDED_AT, MANAGER_ID, AssignmentEndReason.REASSIGNED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot end when result is REJECTED");
        assertThat(history.ending()).isEmpty();
    }

    @Test
    @DisplayName("응답 전 회수된 배정은 거절할 수 없다")
    void cannotRejectWithdrawnAssignment() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT, MANAGER_ID);
        history.end(new AssignmentEnding(DECIDED_AT, MANAGER_ID, AssignmentEndReason.REASSIGNED));

        assertThatThrownBy(() -> history.reject(new Rejection(RejectionReason.OTHER, "기타 사유"), DECIDED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot reject when result is WITHDRAWN");
    }

    @Test
    @DisplayName("저장된 거절 이력을 복원한다")
    void restoresRejectedHistory() {
        AssignmentHistory history = AssignmentHistory.restore(
                SCHEDULE,
                ASSIGNED_AT,
                MANAGER_ID,
                AssignmentResult.REJECTED,
                new Rejection(RejectionReason.OTHER, "기타 사유"),
                DECIDED_AT,
                null);

        assertThat(history.result()).isEqualTo(AssignmentResult.REJECTED);
        assertThat(history.rejectionReason()).contains(RejectionReason.OTHER);
        assertThat(history.assignedAt()).isEqualTo(ASSIGNED_AT);
        assertThat(history.decidedAt()).contains(DECIDED_AT);
    }

    @Test
    @DisplayName("저장된 대기 이력을 복원한다")
    void restoresPendingHistory() {
        AssignmentHistory history = AssignmentHistory.restore(
                SCHEDULE, ASSIGNED_AT, MANAGER_ID, AssignmentResult.PENDING, null, null, null);

        assertThat(history.result()).isEqualTo(AssignmentResult.PENDING);
        assertThat(history.decidedAt()).isEmpty();
    }

    @Test
    @DisplayName("결과가 없는 저장값은 복원하지 않는다")
    void rejectsRestoringWithoutResult() {
        assertThatThrownBy(() -> AssignmentHistory.restore(SCHEDULE, ASSIGNED_AT, MANAGER_ID, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("result must not be null");
    }

    @Test
    @DisplayName("저장된 응답 전 회수 이력을 배정자·종료 기록과 함께 복원한다")
    void restoresWithdrawnHistory() {
        AssignmentEnding ending = new AssignmentEnding(DECIDED_AT, OTHER_MANAGER_ID, AssignmentEndReason.REASSIGNED);

        AssignmentHistory history = AssignmentHistory.restore(
                SCHEDULE, ASSIGNED_AT, MANAGER_ID, AssignmentResult.WITHDRAWN, null, DECIDED_AT, ending);

        assertThat(history.assignedBy()).isEqualTo(MANAGER_ID);
        assertThat(history.result()).isEqualTo(AssignmentResult.WITHDRAWN);
        assertThat(history.decidedAt()).contains(DECIDED_AT);
        assertThat(history.rejectionReason()).isEmpty();
        assertThat(history.ending()).contains(ending);
    }

    @Test
    @DisplayName("거절 사유가 있는 대기 이력은 복원하지 않는다")
    void rejectsRestoringPendingWithReason() {
        assertThatThrownBy(() -> AssignmentHistory.restore(
                        SCHEDULE,
                        ASSIGNED_AT,
                        MANAGER_ID,
                        AssignmentResult.PENDING,
                        new Rejection(RejectionReason.OTHER, "기타 사유"),
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only REJECTED history can have a rejection");
    }

    @Test
    @DisplayName("거절 사유가 있는 응답 전 회수 이력은 복원하지 않는다")
    void rejectsRestoringClosedWithReason() {
        assertThatThrownBy(() -> AssignmentHistory.restore(
                        SCHEDULE,
                        ASSIGNED_AT,
                        MANAGER_ID,
                        AssignmentResult.WITHDRAWN,
                        new Rejection(RejectionReason.OTHER, "기타 사유"),
                        DECIDED_AT,
                        new AssignmentEnding(DECIDED_AT, MANAGER_ID, AssignmentEndReason.REASSIGNED)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only REJECTED history can have a rejection");
    }

    @Test
    @DisplayName("응답 시각이 있는 대기 이력은 복원하지 않는다")
    void rejectsRestoringPendingWithDecidedAt() {
        assertThatThrownBy(() -> AssignmentHistory.restore(
                        SCHEDULE, ASSIGNED_AT, MANAGER_ID, AssignmentResult.PENDING, null, DECIDED_AT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PENDING history must not have decidedAt");
    }

    @Test
    @DisplayName("응답 시각이 없는 확정 이력은 복원하지 않는다")
    void rejectsRestoringDecidedWithoutDecidedAt() {
        assertThatThrownBy(() -> AssignmentHistory.restore(
                        SCHEDULE, ASSIGNED_AT, MANAGER_ID, AssignmentResult.ACCEPTED, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ACCEPTED history must have decidedAt");
    }

    @Test
    @DisplayName("거절 사유 없는 거절 이력은 복원하지 않는다")
    void rejectsRestoringRejectedWithoutReason() {
        assertThatThrownBy(() -> AssignmentHistory.restore(
                        SCHEDULE, ASSIGNED_AT, MANAGER_ID, AssignmentResult.REJECTED, null, DECIDED_AT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rejection must not be null");
    }

    @Test
    @DisplayName("배정한 관리자가 없으면 거부한다")
    void rejectsNullAssignedBy() {
        assertThatThrownBy(() -> new AssignmentHistory(SCHEDULE, ASSIGNED_AT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("assignedBy must not be null");
    }

    @Test
    @DisplayName("응답 전에 끝내면 응답 전 회수로 확정하고 종료 시각을 응답 시각으로 남긴다")
    void endsPendingAssignment() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT, MANAGER_ID);
        AssignmentEnding ending = new AssignmentEnding(DECIDED_AT, OTHER_MANAGER_ID, AssignmentEndReason.UNASSIGNED);

        history.end(ending);

        assertThat(history.result()).isEqualTo(AssignmentResult.WITHDRAWN);
        assertThat(history.decidedAt()).contains(DECIDED_AT);
        assertThat(history.ending()).contains(ending);
        assertThat(history.isOver()).isTrue();
    }

    @Test
    @DisplayName("수락된 뒤에 끝내면 수락 결과는 그대로 두고 종료만 남긴다")
    void endsAcceptedAssignment() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT, MANAGER_ID);
        history.accept(DECIDED_AT);
        AssignmentEnding ending =
                new AssignmentEnding(DECIDED_AT.plusSeconds(60), OTHER_MANAGER_ID, AssignmentEndReason.CANCELLED);

        history.end(ending);

        assertThat(history.result()).isEqualTo(AssignmentResult.ACCEPTED);
        assertThat(history.decidedAt()).contains(DECIDED_AT);
        assertThat(history.ending()).contains(ending);
    }

    @Test
    @DisplayName("이미 끝난 배정은 다시 끝낼 수 없다")
    void cannotEndTwice() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT, MANAGER_ID);
        history.accept(DECIDED_AT);
        history.end(new AssignmentEnding(DECIDED_AT, MANAGER_ID, AssignmentEndReason.UNASSIGNED));

        assertThatThrownBy(() -> history.end(
                        new AssignmentEnding(DECIDED_AT.plusSeconds(1), MANAGER_ID, AssignmentEndReason.CANCELLED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot end an assignment that already ended");
    }

    @Test
    @DisplayName("응답 시각보다 앞서 끝낼 수 없고, 거부해도 이력을 바꾸지 않는다")
    void rejectsEndingBeforeDecision() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT, MANAGER_ID);
        history.accept(DECIDED_AT);

        assertThatThrownBy(() -> history.end(
                        new AssignmentEnding(DECIDED_AT.minusSeconds(1), MANAGER_ID, AssignmentEndReason.UNASSIGNED)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("endedAt must not be before the assignment was assigned or decided");
        assertThat(history.result()).isEqualTo(AssignmentResult.ACCEPTED);
        assertThat(history.decidedAt()).contains(DECIDED_AT);
        assertThat(history.ending()).isEmpty();
    }

    @Test
    @DisplayName("응답을 기다리는 배정은 배정 시각보다 앞서 끝낼 수 없고, 거부해도 대기 상태 그대로다")
    void rejectsEndingPendingBeforeAssignment() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT, MANAGER_ID);

        assertThatThrownBy(() -> history.end(
                        new AssignmentEnding(ASSIGNED_AT.minusSeconds(1), MANAGER_ID, AssignmentEndReason.REASSIGNED)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("endedAt must not be before the assignment was assigned or decided");
        assertThat(history.result()).isEqualTo(AssignmentResult.PENDING);
        assertThat(history.decidedAt()).isEmpty();
        assertThat(history.ending()).isEmpty();
    }

    @Test
    @DisplayName("종료 기록 없이 끝낼 수 없다")
    void rejectsNullEnding() {
        AssignmentHistory history = new AssignmentHistory(SCHEDULE, ASSIGNED_AT, MANAGER_ID);

        assertThatThrownBy(() -> history.end(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ending must not be null");
        assertThat(history.result()).isEqualTo(AssignmentResult.PENDING);
    }

    @Test
    @DisplayName("대기 중이거나 끝나지 않은 수락 배정은 아직 현재 배정이다")
    void isCurrentUntilRejectedOrEnded() {
        AssignmentHistory pending = new AssignmentHistory(SCHEDULE, ASSIGNED_AT, MANAGER_ID);
        AssignmentHistory accepted = new AssignmentHistory(SCHEDULE, ASSIGNED_AT, MANAGER_ID);
        accepted.accept(DECIDED_AT);

        assertThat(pending.isOver()).isFalse();
        assertThat(accepted.isOver()).isFalse();
    }

    @Test
    @DisplayName("수락 시각보다 앞선 종료 기록이 있는 수락 이력은 복원하지 않는다")
    void rejectsRestoringEndingBeforeDecision() {
        assertThatThrownBy(() -> AssignmentHistory.restore(
                        SCHEDULE,
                        ASSIGNED_AT,
                        MANAGER_ID,
                        AssignmentResult.ACCEPTED,
                        null,
                        DECIDED_AT,
                        new AssignmentEnding(DECIDED_AT.minusSeconds(1), MANAGER_ID, AssignmentEndReason.UNASSIGNED)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("endedAt must not be before the assignment was assigned or decided");
    }

    @Test
    @DisplayName("수락 후 끝난 이력을 종료 기록과 함께 복원한다")
    void restoresEndedAcceptedHistory() {
        AssignmentEnding ending = new AssignmentEnding(DECIDED_AT, OTHER_MANAGER_ID, AssignmentEndReason.UNASSIGNED);

        AssignmentHistory history = AssignmentHistory.restore(
                SCHEDULE, ASSIGNED_AT, MANAGER_ID, AssignmentResult.ACCEPTED, null, DECIDED_AT, ending);

        assertThat(history.assignedBy()).isEqualTo(MANAGER_ID);
        assertThat(history.result()).isEqualTo(AssignmentResult.ACCEPTED);
        assertThat(history.ending()).contains(ending);
    }

    @Test
    @DisplayName("응답 전 회수 이력은 종료 기록이 있고 종료 시각이 응답 시각과 같아야 복원한다")
    void requiresMatchingEndingForClosedHistory() {
        assertThatThrownBy(() -> AssignmentHistory.restore(
                        SCHEDULE, ASSIGNED_AT, MANAGER_ID, AssignmentResult.WITHDRAWN, null, DECIDED_AT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("WITHDRAWN history must have an ending");
        assertThatThrownBy(() -> AssignmentHistory.restore(
                        SCHEDULE,
                        ASSIGNED_AT,
                        MANAGER_ID,
                        AssignmentResult.WITHDRAWN,
                        null,
                        DECIDED_AT,
                        new AssignmentEnding(DECIDED_AT.plusSeconds(1), MANAGER_ID, AssignmentEndReason.REASSIGNED)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("WITHDRAWN history must be decided when it ended");
    }

    @Test
    @DisplayName("대기 중이거나 거절된 이력에 종료 기록이 있으면 복원하지 않는다")
    void rejectsEndingOnPendingOrRejectedHistory() {
        AssignmentEnding ending = new AssignmentEnding(DECIDED_AT, MANAGER_ID, AssignmentEndReason.UNASSIGNED);

        assertThatThrownBy(() -> AssignmentHistory.restore(
                        SCHEDULE, ASSIGNED_AT, MANAGER_ID, AssignmentResult.PENDING, null, null, ending))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PENDING history must not have an ending");
        assertThatThrownBy(() -> AssignmentHistory.restore(
                        SCHEDULE,
                        ASSIGNED_AT,
                        MANAGER_ID,
                        AssignmentResult.REJECTED,
                        new Rejection(RejectionReason.OTHER, "기타 사유"),
                        DECIDED_AT,
                        ending))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("REJECTED history must not have an ending");
    }

    @Test
    @DisplayName("거절이 아닌 이력에 거절 사유가 있으면 복원하지 않는다")
    void rejectsRestoringReasonOnNonRejected() {
        assertThatThrownBy(() -> AssignmentHistory.restore(
                        SCHEDULE,
                        ASSIGNED_AT,
                        MANAGER_ID,
                        AssignmentResult.ACCEPTED,
                        new Rejection(RejectionReason.OTHER, "기타 사유"),
                        DECIDED_AT,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only REJECTED history can have a rejection");
    }
}
