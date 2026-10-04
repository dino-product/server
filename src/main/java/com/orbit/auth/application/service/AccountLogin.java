package com.orbit.auth.application.service;

import java.time.Clock;

import com.orbit.auth.application.port.in.command.dto.LoginInfo;
import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.DuplicateIdentityException;
import com.orbit.auth.application.port.out.IssuedAccessToken;
import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.ExternalIdentity;

/**
 * 제공자 증명을 검증한 로그인 서비스가 공통으로 거치는 단계. 외부 식별에 연결된 계정을 찾거나 처음이면 등록하고 자체 Access Token을 발급한다. 호출자의
 * 트랜잭션 안에서 실행한다.
 */
final class AccountLogin {

    private final AccountRepository accounts;
    private final AccessTokenPort accessTokens;
    private final Clock clock;

    AccountLogin(AccountRepository accounts, AccessTokenPort accessTokens, Clock clock) {
        this.accounts = accounts;
        this.accessTokens = accessTokens;
        this.clock = clock;
    }

    LoginInfo login(ExternalIdentity identity) {
        boolean registered = false;
        Account account = accounts.findByIdentity(identity).orElse(null);
        if (account == null) {
            try {
                account = accounts.saveNew(Account.register(identity, clock.instant()));
                registered = true;
            } catch (DuplicateIdentityException exception) {
                // 같은 사용자의 동시 첫 로그인에서 먼저 저장된 계정을 그대로 쓴다.
                account = accounts.findByIdentity(identity).orElseThrow(() -> exception);
            }
        }
        IssuedAccessToken issued = accessTokens.issue(account.id().orElseThrow());
        return new LoginInfo(
                account.id().orElseThrow().value(),
                registered,
                issued.value(),
                issued.token().expiresAt());
    }
}
