package com.orbit.auth.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.ClosedAccountPort;
import com.orbit.auth.domain.AccountId;

/**
 * 닫힌 계정의 폐쇄 표시를 남기는 단계. 커밋 뒤 토큰 폐기와 주기 재확정이 함께 쓴다. 표시는 폐쇄 시각부터 Access Token 수명에 발급 경쟁 여유를 더한 때까지 남기며,
 * 같은 계정을 다시 표시할 때도 원래 폐쇄 시각을 기준으로 남은 기간만 남긴다.
 */
final class ClosedAccountMarks {

    /**
     * 폐쇄 직전에 시작한 로그인이 폐쇄 뒤에 발급한 토큰까지 덮는 여유. 카카오·Apple iOS 로그인은 계정 확인과 발급 사이의 짧은 경쟁이고, Apple 웹 교환 코드(60초)는
     * 교환 때 폐쇄 표시로 막지만 서버 간 시계 차이를 함께 감싼다.
     */
    static final Duration ISSUE_RACE_MARGIN = Duration.ofMinutes(5);

    private static final Duration MIN_RETENTION = Duration.ofSeconds(1);

    private final ClosedAccountPort closedAccounts;
    private final AccessTokenPort accessTokens;
    private final Clock clock;

    ClosedAccountMarks(ClosedAccountPort closedAccounts, AccessTokenPort accessTokens, Clock clock) {
        this.closedAccounts = closedAccounts;
        this.accessTokens = accessTokens;
        this.clock = clock;
    }

    /** 이보다 먼저 닫힌 계정은 폐쇄 전후에 발급한 토큰이 모두 만료돼 표시가 필요 없다. */
    Instant oldestRelevantClosure() {
        return clock.instant().minus(accessTokens.lifetime()).minus(ISSUE_RACE_MARGIN);
    }

    void mark(AccountId accountId, Instant closedAt) {
        Instant expiresAt = closedAt.plus(accessTokens.lifetime()).plus(ISSUE_RACE_MARGIN);
        Duration remaining = Duration.between(clock.instant(), expiresAt);
        if (remaining.compareTo(MIN_RETENTION) < 0) {
            // 폐쇄 전후에 발급한 토큰이 곧 모두 만료되므로 표시할 필요가 없다. Redis 만료는 초 단위라 1초 미만은 저장할 수 없다.
            return;
        }
        closedAccounts.markClosed(accountId, remaining);
    }
}
