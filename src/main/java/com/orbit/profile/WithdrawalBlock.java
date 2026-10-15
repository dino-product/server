package com.orbit.profile;

/** 탈퇴를 막는 사유 하나와, 탈퇴하려면 먼저 정리해야 할 대상(발주사·작업)의 수. 건수의 뜻은 {@link WithdrawalBlockReason}마다 다르다. */
public record WithdrawalBlock(WithdrawalBlockReason reason, int count) {

    public WithdrawalBlock {
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}
