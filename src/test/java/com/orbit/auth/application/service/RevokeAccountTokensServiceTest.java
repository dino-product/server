package com.orbit.auth.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.auth.application.port.in.command.dto.RevokeAccountTokensCommand;
import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.ClosedAccountPort;
import com.orbit.auth.domain.AccountId;

@ExtendWith(MockitoExtension.class)
@DisplayName("계정 단위 토큰 폐기")
class RevokeAccountTokensServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T03:00:00Z");
    private static final Duration LIFETIME = Duration.ofHours(1);

    @Mock
    private ClosedAccountPort closedAccounts;

    @Mock
    private AccessTokenPort accessTokens;

    private RevokeAccountTokensService service;

    @BeforeEach
    void setUp() {
        service = new RevokeAccountTokensService(closedAccounts, accessTokens, Clock.fixed(NOW, ZoneOffset.UTC));
        when(accessTokens.lifetime()).thenReturn(LIFETIME);
    }

    @Test
    @DisplayName("폐쇄 시각부터 Access Token 수명과 발급 경쟁 여유가 지날 때까지 닫힌 계정 표시를 남긴다")
    void marksClosedUntilTokensIssuedAroundClosureExpire() {
        service.revoke(new RevokeAccountTokensCommand(7L, NOW));

        verify(closedAccounts)
                .markClosed(new AccountId(7L), LIFETIME.plus(RevokeAccountTokensService.ISSUE_RACE_MARGIN));
    }

    @Test
    @DisplayName("다시 확정할 때는 원래 폐쇄 시각을 기준으로 남은 기간만 표시한다")
    void marksOnlyRemainingRetentionForEarlierClosure() {
        service.revoke(new RevokeAccountTokensCommand(7L, NOW.minus(Duration.ofMinutes(20))));

        verify(closedAccounts)
                .markClosed(
                        new AccountId(7L),
                        LIFETIME.plus(RevokeAccountTokensService.ISSUE_RACE_MARGIN)
                                .minus(Duration.ofMinutes(20)));
    }

    @Test
    @DisplayName("폐쇄 전에 발급한 토큰이 모두 만료됐으면 표시하지 않는다")
    void skipsWhenAllTokensExpired() {
        service.revoke(new RevokeAccountTokensCommand(
                7L, NOW.minus(LIFETIME).minus(RevokeAccountTokensService.ISSUE_RACE_MARGIN)));

        verify(closedAccounts, never()).markClosed(any(), any());
    }
}
