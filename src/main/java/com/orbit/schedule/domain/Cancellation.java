package com.orbit.schedule.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 작업을 취소한 기록. 취소 시각, 처리자(관리자의 조직 소속), 사유를 담는다. 사유는 필수이고 {@value #MAX_REASON_LENGTH}자까지다. 취소 시각은 저장소
 * 정밀도인 마이크로초로 잘라 둔다.
 */
public record Cancellation(Instant cancelledAt, MembershipId cancelledBy, String reason) {

    /** 취소 사유 최대 길이. */
    public static final int MAX_REASON_LENGTH = 255;

    public Cancellation {
        if (cancelledAt == null) {
            throw new IllegalArgumentException("cancelledAt must not be null");
        }
        if (cancelledBy == null) {
            throw new IllegalArgumentException("cancelledBy must not be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("reason must be at most " + MAX_REASON_LENGTH + " characters");
        }
        cancelledAt = cancelledAt.truncatedTo(ChronoUnit.MICROS);
    }
}
