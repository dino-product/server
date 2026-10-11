package com.orbit.auth.application.service;

import org.springframework.stereotype.Service;

import com.orbit.auth.application.port.in.command.RevokeAccountTokensUseCase;
import com.orbit.auth.application.port.in.command.dto.RevokeAccountTokensCommand;
import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.ClosedAccountPort;
import com.orbit.auth.domain.AccountId;

/**
 * 닫힌 계정에 발급한 Access Token을 모두 폐기한다. 폐쇄 뒤에는 그 계정으로 새 토큰을 발급할 수 없으므로 폐쇄 표시를 Access Token 수명 동안만 남긴다. DB
 * 트랜잭션 없이 폐쇄 표시 저장소만 사용한다.
 */
@Service
public class RevokeAccountTokensService implements RevokeAccountTokensUseCase {

    private final ClosedAccountPort closedAccounts;
    private final AccessTokenPort accessTokens;

    public RevokeAccountTokensService(ClosedAccountPort closedAccounts, AccessTokenPort accessTokens) {
        this.closedAccounts = closedAccounts;
        this.accessTokens = accessTokens;
    }

    @Override
    public void revoke(RevokeAccountTokensCommand command) {
        closedAccounts.markClosed(new AccountId(command.accountId()), accessTokens.lifetime());
    }
}
