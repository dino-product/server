package com.orbit.auth.application.port.out;

import java.time.Duration;

import com.orbit.auth.domain.AccountId;

/** 닫힌 계정을 그 전에 발급한 Access Token이 모두 만료될 때까지 기억한다. 매 요청 인증이 DB 없이 대조할 수 있게 한다. */
public interface ClosedAccountPort {

    void markClosed(AccountId accountId, Duration retention);

    boolean isClosed(AccountId accountId);
}
