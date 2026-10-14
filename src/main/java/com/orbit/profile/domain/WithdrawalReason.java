package com.orbit.profile.domain;

import java.util.Optional;

/** 탈퇴할 때 본인이 남기는 자유 텍스트. 선택 입력이며 앞뒤 공백을 지운 뒤 문자 200자까지 받는다. 비우면 사유 없이 탈퇴한다. */
public record WithdrawalReason(String value) {

    private static final int MAX_LENGTH = 200;

    public WithdrawalReason {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("withdrawal reason must not be blank");
        }
        if (value.codePointCount(0, value.length()) > MAX_LENGTH) {
            throw new IllegalArgumentException("withdrawal reason must be at most " + MAX_LENGTH + " characters");
        }
    }

    /** 입력값으로 사유를 만든다. 비었으면 사유가 없다. */
    public static Optional<WithdrawalReason> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new WithdrawalReason(raw.strip()));
    }
}
