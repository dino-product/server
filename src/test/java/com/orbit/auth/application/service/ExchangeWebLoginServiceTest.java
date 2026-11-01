package com.orbit.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.auth.application.port.in.command.dto.ExchangeWebLoginCommand;
import com.orbit.auth.application.port.in.command.dto.LoginInfo;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.ClosedAccountPort;
import com.orbit.auth.application.port.out.LoginExchangePort;
import com.orbit.auth.application.port.out.PendingWebLogin;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.auth.domain.PkceChallenge;
import com.orbit.shared.error.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("웹 로그인 교환")
class ExchangeWebLoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T01:00:00Z");
    private static final AccountId ACCOUNT_ID = new AccountId(7L);
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final PkceChallenge CHALLENGE = new PkceChallenge("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");

    @Mock
    private LoginExchangePort loginExchanges;

    @Mock
    private AccountRepository accounts;

    @Mock
    private SessionTokenIssuer tokens;

    @Mock
    private ClosedAccountPort closedAccounts;

    @Test
    @DisplayName("일회성 교환 코드를 소비하고 PKCE verifier가 맞으면 그 계정의 Access Token을 발급한다")
    void issuesAccessTokenForPendingLogin() {
        when(loginExchanges.consume(OAuthProvider.APPLE, "exchange-code"))
                .thenReturn(Optional.of(new PendingWebLogin(ACCOUNT_ID, true, CHALLENGE)));
        when(tokens.start(ACCOUNT_ID))
                .thenReturn(new IssuedSessionTokens(
                        "access-token", NOW.plus(Duration.ofHours(1)), "refresh-token", NOW.plus(Duration.ofDays(30))));

        LoginInfo info =
                service().exchange(new ExchangeWebLoginCommand(OAuthProvider.APPLE, "exchange-code", VERIFIER));

        assertThat(info)
                .isEqualTo(new LoginInfo(
                        7L,
                        true,
                        "access-token",
                        NOW.plus(Duration.ofHours(1)),
                        "refresh-token",
                        NOW.plus(Duration.ofDays(30))));
    }

    @Test
    @DisplayName("없거나 이미 쓴 교환 코드면 AUTH-007이다")
    void rejectsUnknownExchangeCode() {
        when(loginExchanges.consume(OAuthProvider.APPLE, "exchange-code")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                        service().exchange(new ExchangeWebLoginCommand(OAuthProvider.APPLE, "exchange-code", VERIFIER)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_LOGIN_EXCHANGE_CODE));
    }

    private ExchangeWebLoginService service() {
        return new ExchangeWebLoginService(
                loginExchanges, closedAccounts, accounts, tokens, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("교환 코드를 만든 뒤 계정이 닫혔으면 토큰을 발급하지 않고 AUTH-007이다")
    void rejectsExchangeForClosedAccount() {
        when(loginExchanges.consume(OAuthProvider.APPLE, "exchange-code"))
                .thenReturn(Optional.of(new PendingWebLogin(ACCOUNT_ID, false, CHALLENGE)));
        when(closedAccounts.isClosed(ACCOUNT_ID)).thenReturn(true);

        assertThatThrownBy(() ->
                        service().exchange(new ExchangeWebLoginCommand(OAuthProvider.APPLE, "exchange-code", VERIFIER)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_LOGIN_EXCHANGE_CODE));
        verify(tokens, never()).start(any());
    }

    @Test
    @DisplayName("교환 코드를 가로채도 로그인을 시작한 클라이언트의 code_verifier가 아니면 AUTH-007이다")
    void rejectsWrongCodeVerifier() {
        when(loginExchanges.consume(OAuthProvider.APPLE, "exchange-code"))
                .thenReturn(Optional.of(new PendingWebLogin(ACCOUNT_ID, true, CHALLENGE)));

        assertThatThrownBy(() -> service()
                        .exchange(new ExchangeWebLoginCommand(
                                OAuthProvider.APPLE, "exchange-code", "attacker-verifier-attacker-verifier-attacker")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_LOGIN_EXCHANGE_CODE));
    }
}
