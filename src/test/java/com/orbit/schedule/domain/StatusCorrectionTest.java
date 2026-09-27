package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("상태 정정 기록")
class StatusCorrectionTest {

    private static final Instant AT = Instant.parse("2026-09-21T01:00:00Z");
    private static final MembershipId OWNER_ID = new MembershipId(98L);
    private static final CompletionReport REPORT = new CompletionReport(null, null, null, "완료", null, null);
    private static final Cancellation CANCELLATION = new Cancellation(AT, new MembershipId(99L), "고객 요청");

    @Test
    @DisplayName("정정 시각과 보관한 시각은 마이크로초로 자른다")
    void truncatesTimes() {
        StatusCorrection correction = new StatusCorrection(
                WorkStatus.COMPLETED,
                WorkStatus.IN_PROGRESS,
                AT.plusNanos(1_999),
                OWNER_ID,
                "정정",
                REPORT,
                AT.plusNanos(999),
                null,
                null,
                null);

        assertThat(correction.correctedAt()).isEqualTo(AT.plusNanos(1_000));
        assertThat(correction.retiredCompletedAt()).isEqualTo(AT);
    }

    @Test
    @DisplayName("허용하지 않는 전이의 기록은 만들 수 없다")
    void rejectsDisallowedTransition() {
        assertThatThrownBy(() -> new StatusCorrection(
                        WorkStatus.ACCEPTED, WorkStatus.REGISTERED, AT, OWNER_ID, "정정", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("status correction from ACCEPTED to REGISTERED is not allowed");
    }

    @Test
    @DisplayName("완료 → 작업중은 완료보고·완료 시각을 보관하고 시작 시각·취소 기록은 보관하지 않는다")
    void completedCorrectionKeepsReportOnly() {
        assertThatThrownBy(() -> new StatusCorrection(
                        WorkStatus.COMPLETED, WorkStatus.IN_PROGRESS, AT, OWNER_ID, "정정", null, AT, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("correction from COMPLETED must keep retiredReport");
        assertThatThrownBy(() -> new StatusCorrection(
                        WorkStatus.COMPLETED, WorkStatus.IN_PROGRESS, AT, OWNER_ID, "정정", REPORT, AT, AT, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("correction from COMPLETED must not keep retiredStartedAt");
        assertThatThrownBy(() -> new StatusCorrection(
                        WorkStatus.COMPLETED,
                        WorkStatus.IN_PROGRESS,
                        AT,
                        OWNER_ID,
                        "정정",
                        REPORT,
                        AT,
                        null,
                        CANCELLATION,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("correction from COMPLETED must not keep retiredCancellation");
    }

    @Test
    @DisplayName("작업중 → 수락됨은 시작 시각만 보관한다")
    void inProgressCorrectionKeepsStartOnly() {
        assertThatThrownBy(() -> new StatusCorrection(
                        WorkStatus.IN_PROGRESS, WorkStatus.ACCEPTED, AT, OWNER_ID, "정정", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("correction from IN_PROGRESS must keep retiredStartedAt");
        assertThatThrownBy(() -> new StatusCorrection(
                        WorkStatus.IN_PROGRESS, WorkStatus.ACCEPTED, AT, OWNER_ID, "정정", REPORT, null, AT, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("correction from IN_PROGRESS must not keep retiredReport");
    }

    @Test
    @DisplayName("취소 → 대기함은 취소 기록을 반드시, 시작 시각은 작업중에 취소됐을 때만 보관한다")
    void cancelledCorrectionKeepsCancellation() {
        assertThat(new StatusCorrection(
                                WorkStatus.CANCELLED,
                                WorkStatus.REGISTERED,
                                AT,
                                OWNER_ID,
                                "정정",
                                null,
                                null,
                                null,
                                CANCELLATION,
                                null)
                        .retiredStartedAt())
                .isNull();
        assertThatThrownBy(() -> new StatusCorrection(
                        WorkStatus.CANCELLED, WorkStatus.REGISTERED, AT, OWNER_ID, "정정", null, null, AT, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("correction from CANCELLED must keep retiredCancellation");
    }

    @Test
    @DisplayName("되돌린 배정 순번은 취소 → 대기함에만 있고 1 이상이다")
    void restoredAssignmentNumberOnlyForCancelled() {
        assertThat(new StatusCorrection(
                                WorkStatus.CANCELLED,
                                WorkStatus.REGISTERED,
                                AT,
                                OWNER_ID,
                                "정정",
                                null,
                                null,
                                null,
                                CANCELLATION,
                                2)
                        .restoredAssignmentNumber())
                .isEqualTo(2);
        assertThatThrownBy(() -> new StatusCorrection(
                        WorkStatus.CANCELLED,
                        WorkStatus.REGISTERED,
                        AT,
                        OWNER_ID,
                        "정정",
                        null,
                        null,
                        null,
                        CANCELLATION,
                        0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("restoredAssignmentNumber must be positive");
        assertThatThrownBy(() -> new StatusCorrection(
                        WorkStatus.IN_PROGRESS, WorkStatus.ACCEPTED, AT, OWNER_ID, "정정", null, null, AT, null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("correction from IN_PROGRESS must not keep restoredAssignmentNumber");
    }

    @Test
    @DisplayName("보관한 시각은 정정 시각보다 늦을 수 없다")
    void rejectsRetiredTimeAfterCorrection() {
        assertThatThrownBy(() -> new StatusCorrection(
                        WorkStatus.COMPLETED,
                        WorkStatus.IN_PROGRESS,
                        AT,
                        OWNER_ID,
                        "정정",
                        REPORT,
                        AT.plusSeconds(1),
                        null,
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("retiredCompletedAt must not be after correctedAt");
        assertThatThrownBy(() -> new StatusCorrection(
                        WorkStatus.IN_PROGRESS,
                        WorkStatus.ACCEPTED,
                        AT,
                        OWNER_ID,
                        "정정",
                        null,
                        null,
                        AT.plusSeconds(1),
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("retiredStartedAt must not be after correctedAt");
        assertThatThrownBy(() -> new StatusCorrection(
                        WorkStatus.CANCELLED,
                        WorkStatus.REGISTERED,
                        AT.minusSeconds(1),
                        OWNER_ID,
                        "정정",
                        null,
                        null,
                        null,
                        CANCELLATION,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("retiredCancellation.cancelledAt must not be after correctedAt");
    }
}
