package com.orbit.auth.application.service;

import java.time.Clock;

import org.springframework.stereotype.Service;

import com.orbit.auth.application.port.in.command.ReconfirmClosedAccountsUseCase;
import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.ClosedAccountPort;
import com.orbit.auth.domain.Account;

/**
 * 토큰이 아직 남았을 수 있는 최근 폐쇄 계정의 폐쇄 표시를 다시 남긴다. 커밋 뒤 표시 저장이 실패해도 탈퇴 응답은 성공하므로, 주기 작업이 이 단계로 표시를 되살린다.
 * 표시는 멱등이라 이미 있는 표시를 다시 남겨도 보관 기간이 늘지 않는다. 조회는 저장소 단위 읽기 트랜잭션으로 하고 표시는 그 밖에서 남긴다.
 */
@Service
public class ReconfirmClosedAccountsService implements ReconfirmClosedAccountsUseCase {

    private final AccountRepository accounts;
    private final ClosedAccountMarks marks;

    public ReconfirmClosedAccountsService(
            AccountRepository accounts, ClosedAccountPort closedAccounts, AccessTokenPort accessTokens, Clock clock) {
        this.accounts = accounts;
        this.marks = new ClosedAccountMarks(closedAccounts, accessTokens, clock);
    }

    @Override
    public void reconfirm() {
        for (Account account : accounts.listClosedSince(marks.oldestRelevantClosure())) {
            marks.mark(account.id().orElseThrow(), account.closedAt().orElseThrow());
        }
    }
}
