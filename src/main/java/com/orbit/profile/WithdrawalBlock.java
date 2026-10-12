package com.orbit.profile;

/**
 * 탈퇴를 막는 사유 하나. 사유 코드는 구현 모듈이 정하는 대문자 상수(예: {@code SOLE_OWNER}, {@code WORK_IN_PROGRESS})이고, 건수는 탈퇴하려면 먼저
 * 정리해야 할 대상(발주사·작업)의 수다. 클라이언트는 사유 코드로 안내 문구와 해결 경로를 고른다.
 */
public record WithdrawalBlock(String reason, int count) {

    public WithdrawalBlock {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}
