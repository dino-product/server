package com.orbit.auth.application.service;

import java.time.Clock;

import com.orbit.auth.application.port.in.command.dto.LoginInfo;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.DuplicateIdentityException;
import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.ExternalIdentity;

/**
 * 제공자 증명을 검증한 로그인 서비스가 공통으로 거치는 단계. 외부 식별에 연결된 계정을 찾거나 처음이면 등록하고 기기 세션을 시작해 Access Token과 refresh token을 발급한다. 웹 콜백처럼
 * 계정 확인과 토큰 발급 사이에 클라이언트 복귀가 끼면 두 단계를 나눠 쓴다.
 */
final class AccountLogin {

    private final AccountRepository accounts;
    private final SessionTokenIssuer tokens;
    private final Clock clock;

    AccountLogin(AccountRepository accounts, SessionTokenIssuer tokens, Clock clock) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.clock = clock;
    }

    LoginInfo login(ExternalIdentity identity) {
        return issue(findOrRegister(identity));
    }

    /** 외부 식별에 연결된 계정을 찾고, 없으면 등록한다. 동시 첫 로그인에서 먼저 저장된 계정을 다시 조회해 쓴다. */
    SignedInAccount findOrRegister(ExternalIdentity identity) {
        Account account = accounts.findByIdentity(identity).orElse(null);
        if (account != null) {
            return new SignedInAccount(account.id().orElseThrow(), false);
        }
        try {
            return new SignedInAccount(
                    accounts.saveNew(Account.register(identity, clock.instant()))
                            .id()
                            .orElseThrow(),
                    true);
        } catch (DuplicateIdentityException exception) {
            // 같은 사용자의 동시 첫 로그인에서 먼저 저장된 계정을 그대로 쓴다.
            AccountId existing = accounts.findByIdentity(identity)
                    .orElseThrow(() -> exception)
                    .id()
                    .orElseThrow();
            return new SignedInAccount(existing, false);
        }
    }

    LoginInfo issue(SignedInAccount login) {
        IssuedSessionTokens issued = tokens.start(login.accountId());
        return new LoginInfo(
                login.accountId().value(),
                login.registered(),
                issued.accessToken(),
                issued.accessTokenExpiresAt(),
                issued.refreshToken(),
                issued.refreshTokenExpiresAt());
    }
}
