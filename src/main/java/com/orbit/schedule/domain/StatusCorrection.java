package com.orbit.schedule.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 총관리자가 작업 상태를 한 단계 되돌린 기록. 전후 상태·시각·처리자(총관리자의 조직 소속)·사유와, 되돌리면서 작업에서 치운 데이터를 보관한다. 허용 전이는
 * {@link WorkStatus#canBeCorrectedTo}가 정하며, 전이마다 보관하는 데이터가 다르다.
 *
 * <ul>
 *   <li>완료 → 작업중: 제출됐던 완료보고와 완료 시각. 기사는 완료보고를 다시 제출할 수 있다.
 *   <li>작업중 → 수락됨: 시작 시각.
 *   <li>취소 → 대기함: 취소 기록과, 작업중에 취소됐다면 시작 시각. 배정이 있던 채로 취소됐다면 그 배정의 순번(배정 이력의 1부터 시작하는 위치)을 남겨,
 *       배정 이력의 어느 취소 종료를 되돌렸는지 시각과 관계없이 정확히 가리킨다.
 * </ul>
 *
 * 사유는 필수이고 {@value #MAX_REASON_LENGTH}자까지다. 보관한 시각은 정정 시각보다 늦을 수 없다. 시각은 저장소 정밀도인 마이크로초로 잘라 둔다.
 */
public record StatusCorrection(
        WorkStatus from,
        WorkStatus to,
        Instant correctedAt,
        MembershipId correctedBy,
        String reason,
        CompletionReport retiredReport,
        Instant retiredCompletedAt,
        Instant retiredStartedAt,
        Cancellation retiredCancellation,
        Integer restoredAssignmentNumber) {

    /** 정정 사유 최대 길이. */
    public static final int MAX_REASON_LENGTH = 255;

    public StatusCorrection {
        if (from == null) {
            throw new IllegalArgumentException("from must not be null");
        }
        requireValidInput(to, correctedAt, correctedBy, reason);
        if (!from.canBeCorrectedTo(to)) {
            throw new IllegalArgumentException("status correction from " + from + " to " + to + " is not allowed");
        }
        boolean completedToInProgress = from == WorkStatus.COMPLETED;
        boolean inProgressToAccepted = from == WorkStatus.IN_PROGRESS;
        boolean cancelledToRegistered = from == WorkStatus.CANCELLED;
        requireRetained(from, completedToInProgress, retiredReport, "retiredReport");
        requireRetained(from, completedToInProgress, retiredCompletedAt, "retiredCompletedAt");
        if (inProgressToAccepted && retiredStartedAt == null) {
            throw new IllegalArgumentException("correction from IN_PROGRESS must keep retiredStartedAt");
        }
        if (completedToInProgress && retiredStartedAt != null) {
            throw new IllegalArgumentException("correction from COMPLETED must not keep retiredStartedAt");
        }
        requireRetained(from, cancelledToRegistered, retiredCancellation, "retiredCancellation");
        if (!cancelledToRegistered && restoredAssignmentNumber != null) {
            throw new IllegalArgumentException("correction from " + from + " must not keep restoredAssignmentNumber");
        }
        if (restoredAssignmentNumber != null && restoredAssignmentNumber < 1) {
            throw new IllegalArgumentException("restoredAssignmentNumber must be positive");
        }
        correctedAt = correctedAt.truncatedTo(ChronoUnit.MICROS);
        retiredCompletedAt = retiredCompletedAt == null ? null : retiredCompletedAt.truncatedTo(ChronoUnit.MICROS);
        retiredStartedAt = retiredStartedAt == null ? null : retiredStartedAt.truncatedTo(ChronoUnit.MICROS);
        requireNotAfter(retiredCompletedAt, correctedAt, "retiredCompletedAt");
        requireNotAfter(retiredStartedAt, correctedAt, "retiredStartedAt");
        requireNotAfter(
                retiredCancellation == null ? null : retiredCancellation.cancelledAt(),
                correctedAt,
                "retiredCancellation.cancelledAt");
    }

    private static void requireNotAfter(Instant retired, Instant correctedAt, String field) {
        if (retired != null && retired.isAfter(correctedAt)) {
            throw new IllegalArgumentException(field + " must not be after correctedAt");
        }
    }

    /** 정정 요청의 입력(도착 상태·시각·처리자·사유)을 상태와 관계없이 먼저 확인한다. */
    static void requireValidInput(WorkStatus to, Instant correctedAt, MembershipId correctedBy, String reason) {
        if (to == null) {
            throw new IllegalArgumentException("to must not be null");
        }
        if (correctedAt == null) {
            throw new IllegalArgumentException("correctedAt must not be null");
        }
        if (correctedBy == null) {
            throw new IllegalArgumentException("correctedBy must not be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("reason must be at most " + MAX_REASON_LENGTH + " characters");
        }
    }

    private static void requireRetained(WorkStatus from, boolean required, Object value, String field) {
        if (required && value == null) {
            throw new IllegalArgumentException("correction from " + from + " must keep " + field);
        }
        if (!required && value != null) {
            throw new IllegalArgumentException("correction from " + from + " must not keep " + field);
        }
    }
}
