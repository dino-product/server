package com.orbit.auth.application.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.ClosedAccountPort;
import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.AccountId;

@ExtendWith(MockitoExtension.class)
@DisplayName("최근 폐쇄 계정 표시 재확정")
class ReconfirmClosedAccountsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T03:00:00Z");
    private static final Duration LIFETIME = Duration.ofHours(1);

    @Mock
    private AccountRepository accounts;

    @Mock
    private ClosedAccountPort closedAccounts;

    @Mock
    private AccessTokenPort accessTokens;

    private ReconfirmClosedAccountsService service;

    @BeforeEach
    void setUp() {
        service = new ReconfirmClosedAccountsService(
                accounts, closedAccounts, accessTokens, Clock.fixed(NOW, ZoneOffset.UTC));
        when(accessTokens.lifetime()).thenReturn(LIFETIME);
    }

    @Test
    @DisplayName("토큰이 아직 남았을 수 있는 최근 폐쇄 계정의 표시를 원래 폐쇄 시각 기준으로 다시 남긴다")
    void remarksRecentlyClosedAccounts() {
        Instant oldest = NOW.minus(LIFETIME).minus(ClosedAccountMarks.ISSUE_RACE_MARGIN);
        Instant closedAt = NOW.minus(Duration.ofMinutes(10));
        when(accounts.listClosedSince(oldest))
                .thenReturn(
                        List.of(Account.reconstituteClosed(new AccountId(7L), closedAt.minusSeconds(60), closedAt)));

        service.reconfirm();

        verify(closedAccounts)
                .markClosed(
                        new AccountId(7L),
                        LIFETIME.plus(ClosedAccountMarks.ISSUE_RACE_MARGIN).minus(Duration.ofMinutes(10)));
    }
}
