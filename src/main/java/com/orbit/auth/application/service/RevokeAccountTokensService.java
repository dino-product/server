package com.orbit.auth.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.orbit.auth.application.port.in.command.RevokeAccountTokensUseCase;
import com.orbit.auth.application.port.in.command.dto.RevokeAccountTokensCommand;
import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.ClosedAccountPort;
import com.orbit.auth.domain.AccountId;

/**
 * 닫힌 계정에 발급한 Access Token을 모두 폐기한다. 매 요청 인증이 대조하는 폐쇄 표시를 폐쇄 시각부터 Access Token 수명에 발급 경쟁 여유를 더한 때까지
 * 남긴다. 같은 계정을 다시 닫아 폐기를 다시 확정할 때도 원래 폐쇄 시각을 기준으로 남은 기간만 남긴다. DB 트랜잭션 없이 폐쇄 표시 저장소만 사용한다.
 */
@Service
public class RevokeAccountTokensService implements RevokeAccountTokensUseCase {

    /**
     * 폐쇄 직전에 시작한 로그인이 폐쇄 뒤에 발급한 토큰까지 덮는 여유. 카카오·Apple iOS 로그인은 계정 확인과 발급 사이의 짧은 경쟁이고, Apple 웹 교환 코드(60초)는
     * 교환 때 폐쇄 표시로 막지만 서버 간 시계 차이를 함께 감싼다.
     */
    static final Duration ISSUE_RACE_MARGIN = Duration.ofMinutes(5);

    private final ClosedAccountPort closedAccounts;
    private final AccessTokenPort accessTokens;
    private final Clock clock;

    public RevokeAccountTokensService(ClosedAccountPort closedAccounts, AccessTokenPort accessTokens, Clock clock) {
        this.closedAccounts = closedAccounts;
        this.accessTokens = accessTokens;
        this.clock = clock;
    }

    @Override
    public void revoke(RevokeAccountTokensCommand command) {
        Instant expiresAt = command.closedAt().plus(accessTokens.lifetime()).plus(ISSUE_RACE_MARGIN);
        Duration remaining = Duration.between(clock.instant(), expiresAt);
        if (remaining.isZero() || remaining.isNegative()) {
            // 폐쇄 전후에 발급한 토큰이 모두 만료됐으므로 표시할 필요가 없다.
            return;
        }
        closedAccounts.markClosed(new AccountId(command.accountId()), remaining);
    }
}
