package com.orbit.auth.application.service;

import java.time.Clock;

import org.springframework.stereotype.Service;

import com.orbit.auth.application.port.in.command.RevokeAccountTokensUseCase;
import com.orbit.auth.application.port.in.command.dto.RevokeAccountTokensCommand;
import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.ClosedAccountPort;
import com.orbit.auth.domain.AccountId;

/**
 * 닫힌 계정에 발급한 Access Token을 모두 폐기한다. 매 요청 인증이 대조하는 폐쇄 표시를 남긴다({@link ClosedAccountMarks}). DB 트랜잭션 없이 폐쇄 표시
 * 저장소만 사용한다.
 */
@Service
public class RevokeAccountTokensService implements RevokeAccountTokensUseCase {

    private final ClosedAccountMarks marks;

    public RevokeAccountTokensService(ClosedAccountPort closedAccounts, AccessTokenPort accessTokens, Clock clock) {
        this.marks = new ClosedAccountMarks(closedAccounts, accessTokens, clock);
    }

    @Override
    public void revoke(RevokeAccountTokensCommand command) {
        marks.mark(new AccountId(command.accountId()), command.closedAt());
    }
}
