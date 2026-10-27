package com.orbit.auth.domain;

/** 기기 세션에 제출된 refresh token을 판정한 결과. */
public enum RefreshOutcome {
    /** 현재 토큰이라 다음 토큰으로 회전했다. */
    ROTATED,
    /** 회전 직후 유예 안에 직전 토큰이 다시 왔다. 상태는 그대로이고 이미 회전한 다음 토큰을 다시 돌려준다. */
    REPLAYED,
    /** 유예 안의 직전 토큰이지만 다시 만든 다음 토큰이 현재 토큰과 다르다(파생 키가 바뀜). 세션은 두고 거절한다. */
    STALE,
    /** 이미 회전된 토큰이 유예 뒤에 왔거나 어느 토큰과도 맞지 않는다. 탈취로 보고 세션을 철회한다. */
    REUSED,
    /** 미사용 수명이 지났다. */
    EXPIRED
}
