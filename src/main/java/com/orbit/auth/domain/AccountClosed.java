package com.orbit.auth.domain;

import java.time.Instant;

/** 계정이 닫혔다는 auth 안의 사실. 커밋 뒤 그 계정의 Access Token을 폐기하는 데 쓴다. 다른 모듈에 공개하지 않는다. */
public record AccountClosed(AccountId accountId, Instant closedAt) {

    public AccountClosed {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId must not be null");
        }
        if (closedAt == null) {
            throw new IllegalArgumentException("closedAt must not be null");
        }
    }
}
