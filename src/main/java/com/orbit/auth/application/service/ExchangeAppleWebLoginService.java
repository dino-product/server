package com.orbit.auth.application.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.auth.application.port.in.command.ExchangeAppleWebLoginUseCase;
import com.orbit.auth.application.port.in.command.dto.ExchangeAppleWebLoginCommand;
import com.orbit.auth.application.port.in.command.dto.LoginInfo;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.AppleLoginExchangePort;
import com.orbit.auth.application.port.out.ClosedAccountPort;
import com.orbit.auth.application.port.out.PendingAppleLogin;
import com.orbit.shared.error.BusinessException;

/**
 * 웹·Android Apple 콜백이 넘긴 일회성 교환 코드를 Access Token으로 바꾼다. Access Token이 복귀 URL에 드러나지 않게 하려는 단계이며, 코드는 한 번만
 * 쓸 수 있고 로그인을 시작한 클라이언트의 PKCE code_verifier가 맞아야 한다(Android 앱 스킴 가로채기·웹 login CSRF 방지). 외부 HTTP를 기다리지
 * 않으므로 기기 세션의 만료 정리와 새 세션 저장을 한 트랜잭션으로 묶는다.
 */
@Service
public class ExchangeAppleWebLoginService implements ExchangeAppleWebLoginUseCase {

    private final AppleLoginExchangePort loginExchanges;
    private final ClosedAccountPort closedAccounts;
    private final AccountLogin accountLogin;

    public ExchangeAppleWebLoginService(
            AppleLoginExchangePort loginExchanges,
            ClosedAccountPort closedAccounts,
            AccountRepository accounts,
            SessionTokenIssuer tokens,
            Clock clock) {
        this.loginExchanges = loginExchanges;
        this.closedAccounts = closedAccounts;
        this.accountLogin = new AccountLogin(accounts, tokens, clock);
    }

    @Override
    @Transactional
    public LoginInfo exchange(ExchangeAppleWebLoginCommand command) {
        PendingAppleLogin pending = (command.code() == null
                ? null
                : loginExchanges.consume(command.code()).orElse(null));
        // 코드는 먼저 소비하므로 verifier가 틀리면 그 코드는 다시 시도할 수 없다.
        if (pending == null || !pending.codeChallenge().matches(command.codeVerifier())) {
            throw new BusinessException(AuthErrorCode.INVALID_APPLE_LOGIN_EXCHANGE_CODE);
        }
        // 콜백 뒤 교환 전에 계정이 닫혔으면 닫힌 계정의 토큰을 내주지 않는다. 다시 로그인하면 새 계정이 된다.
        if (closedAccounts.isClosed(pending.accountId())) {
            throw new BusinessException(AuthErrorCode.INVALID_APPLE_LOGIN_EXCHANGE_CODE);
        }
        return accountLogin.issue(new SignedInAccount(pending.accountId(), pending.registered()));
    }
}
