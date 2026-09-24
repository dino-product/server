package com.orbit.schedule.domain;

/**
 * 기사가 배정을 거절한 사유. 목록에서 고른 사유가 기타({@link RejectionReason#OTHER})면 무엇 때문인지 메모(최대 {@value #MAX_NOTE_LENGTH}자)를 반드시 남기고,
 * 다른 사유에는 메모를 받지 않는다. 메모의 빈 문자열·공백은 미입력(null)으로 정규화한다.
 */
public record Rejection(RejectionReason reason, String note) {

    public static final int MAX_NOTE_LENGTH = 255;

    public Rejection {
        if (reason == null) {
            throw new IllegalArgumentException("rejectionReason must not be null");
        }
        note = note == null || note.isBlank() ? null : note;
        if (reason == RejectionReason.OTHER && note == null) {
            throw new IllegalArgumentException("rejection note is required for OTHER");
        }
        if (reason != RejectionReason.OTHER && note != null) {
            throw new IllegalArgumentException("rejection note is only for OTHER");
        }
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("rejection note must be at most " + MAX_NOTE_LENGTH + " characters");
        }
    }
}
