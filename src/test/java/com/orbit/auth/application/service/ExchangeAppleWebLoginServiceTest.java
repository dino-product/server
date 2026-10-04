package com.orbit.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.orbit.auth.application.port.in.command.dto.ExchangeAppleWebLoginCommand;
import com.orbit.auth.application.port.in.command.dto.LoginInfo;
import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.AppleLoginExchangePort;
import com.orbit.auth.application.port.out.IssuedAccessToken;
import com.orbit.auth.application.port.out.PendingAppleLogin;
import com.orbit.auth.domain.AccessToken;
import com.orbit.auth.domain.AccountId;
import com.orbit.shared.error.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("Apple 웹 로그인 교환")
class ExchangeAppleWebLoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T01:00:00Z");
    private static final AccountId ACCOUNT_ID = new AccountId(7L);

    @Mock
    private AppleLoginExchangePort loginExchanges;

    @Mock
    private AccountRepository accounts;

    @Mock
    private AccessTokenPort accessTokens;

    @Test
    @DisplayName("일회성 교환 코드를 소비하고 그 계정의 Access Token을 발급한다")
    void issuesAccessTokenForPendingLogin() {
        when(loginExchanges.consume("exchange-code")).thenReturn(Optional.of(new PendingAppleLogin(ACCOUNT_ID, true)));
        when(accessTokens.issue(ACCOUNT_ID))
                .thenReturn(new IssuedAccessToken(
                        "access-token", new AccessToken("jti", ACCOUNT_ID, NOW, NOW.plus(Duration.ofHours(1)))));

        LoginInfo info = service().exchange(new ExchangeAppleWebLoginCommand("exchange-code"));

        assertThat(info).isEqualTo(new LoginInfo(7L, true, "access-token", NOW.plus(Duration.ofHours(1))));
    }

    @Test
    @DisplayName("없거나 이미 쓴 교환 코드면 AUTH-007이다")
    void rejectsUnknownExchangeCode() {
        when(loginExchanges.consume("exchange-code")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().exchange(new ExchangeAppleWebLoginCommand("exchange-code")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_APPLE_LOGIN_EXCHANGE_CODE));
    }

    private ExchangeAppleWebLoginService service() {
        return new ExchangeAppleWebLoginService(
                loginExchanges, accounts, accessTokens, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
