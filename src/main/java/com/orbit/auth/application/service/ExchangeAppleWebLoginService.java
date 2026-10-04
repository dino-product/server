package com.orbit.auth.application.service;

import java.time.Clock;

import org.springframework.stereotype.Service;

import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.auth.application.port.in.command.ExchangeAppleWebLoginUseCase;
import com.orbit.auth.application.port.in.command.dto.ExchangeAppleWebLoginCommand;
import com.orbit.auth.application.port.in.command.dto.LoginInfo;
import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.AppleLoginExchangePort;
import com.orbit.auth.application.port.out.PendingAppleLogin;
import com.orbit.shared.error.BusinessException;

/**
 * 웹·Android Apple 콜백이 넘긴 일회성 교환 코드를 Access Token으로 바꾼다. Access Token이 복귀 URL에 드러나지 않게 하려는 단계이며, 코드는 한 번만
 * 쓸 수 있다. DB 트랜잭션 없이 교환 코드 저장소와 토큰 발급기만 사용한다.
 */
@Service
public class ExchangeAppleWebLoginService implements ExchangeAppleWebLoginUseCase {

    private final AppleLoginExchangePort loginExchanges;
    private final AccountLogin accountLogin;

    public ExchangeAppleWebLoginService(
            AppleLoginExchangePort loginExchanges,
            AccountRepository accounts,
            AccessTokenPort accessTokens,
            Clock clock) {
        this.loginExchanges = loginExchanges;
        this.accountLogin = new AccountLogin(accounts, accessTokens, clock);
    }

    @Override
    public LoginInfo exchange(ExchangeAppleWebLoginCommand command) {
        PendingAppleLogin pending = (command.code() == null
                ? null
                : loginExchanges.consume(command.code()).orElse(null));
        if (pending == null) {
            throw new BusinessException(AuthErrorCode.INVALID_APPLE_LOGIN_EXCHANGE_CODE);
        }
        return accountLogin.issue(new SignedInAccount(pending.accountId(), pending.registered()));
    }
}
