package com.orbit.organization.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * 참여 요청 거절 사유. 신청자의 거절 알림·화면에 그대로 보인다. 최대 길이는 Notion에 없어 구현에서 정했다(O-19).
 *
 * @param value 앞뒤 공백을 지운, 비어 있지 않은 사유
 */
public record RejectReason(String value) {

    /** 코드 포인트로 센 최대 길이. 저장 컬럼({@code VARCHAR(255)})과 같다. */
    public static final int MAX_LENGTH = 255;

    public RejectReason {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank() || !value.strip().equals(value)) {
            throw new IllegalArgumentException("rejectReason must be stripped and not blank");
        }
        if (value.codePointCount(0, value.length()) > MAX_LENGTH) {
            throw new IllegalArgumentException("rejectReason must not exceed " + MAX_LENGTH + " characters");
        }
        // PostgreSQL 문자열은 NUL을 저장하지 못한다.
        if (value.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("rejectReason must not contain NUL");
        }
    }

    /**
     * 입력한 사유의 앞뒤 공백을 지운다. 비었거나 공백뿐이면 사유 없음이다.
     *
     * @throws IllegalArgumentException 최대 길이를 넘거나 NUL 문자가 있을 때
     */
    public static Optional<RejectReason> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new RejectReason(raw.strip()));
    }
}
