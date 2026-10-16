package com.orbit.schedule.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 작업을 취소한 기록. 취소 시각, 처리자(관리자의 조직 소속), 목록에서 고른 사유를 담는다. 사유가 기타({@link CancellationReason#OTHER})면 무엇 때문인지
 * 메모(최대 {@value #MAX_NOTE_LENGTH}자)를 반드시 남기고, 다른 사유에는 메모를 받지 않는다. 메모의 빈 문자열·공백은 미입력(null)으로 정규화한다. 취소
 * 시각은 저장소 정밀도인 마이크로초로 잘라 둔다.
 */
public record Cancellation(Instant cancelledAt, MembershipId cancelledBy, CancellationReason reason, String note) {

    /** 기타 사유 메모 최대 길이. */
    public static final int MAX_NOTE_LENGTH = 255;

    public Cancellation {
        if (cancelledAt == null) {
            throw new IllegalArgumentException("cancelledAt must not be null");
        }
        if (cancelledBy == null) {
            throw new IllegalArgumentException("cancelledBy must not be null");
        }
        if (reason == null) {
            throw new IllegalArgumentException("cancellation reason must not be null");
        }
        note = note == null || note.isBlank() ? null : note;
        if (reason == CancellationReason.OTHER && note == null) {
            throw new IllegalArgumentException("cancellation note is required for OTHER");
        }
        if (reason != CancellationReason.OTHER && note != null) {
            throw new IllegalArgumentException("cancellation note is only for OTHER");
        }
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("cancellation note must be at most " + MAX_NOTE_LENGTH + " characters");
        }
        cancelledAt = cancelledAt.truncatedTo(ChronoUnit.MICROS);
    }
}
