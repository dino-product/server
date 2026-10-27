package com.orbit.profile;

/**
 * 탈퇴를 막는 사유. [조직·계정] 정책 §5.1이 계정 탈퇴 차단 조건을 이 셋으로 정하므로 profile이 목록을 소유하고, 각 조건의 상태를 소유한 모듈이
 * {@link WithdrawalBlocker}로 판정한다. 클라이언트는 이 값으로 안내 문구와 해결 경로를 고른다.
 */
public enum WithdrawalBlockReason {

    /** 어느 발주사에서든 유일한 총관리자다. 건수는 그런 발주사의 수다. */
    SOLE_OWNER,

    /** 기사로 소속된 발주사에 작업중 작업이 있다. 건수는 그 작업의 수다. */
    WORK_IN_PROGRESS,

    /** 기사로 소속된 발주사에 응답 전(배정됨) 작업이 있다. 건수는 그 작업의 수다. 이름은 [작업] 정책 §9의 배정됨 코드 {@code PENDING_ACCEPT}를 따른다. */
    WORK_PENDING_ACCEPT
}
