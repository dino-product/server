package com.orbit.auth.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.auth.AccountClosure;
import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.ProviderUnlinkRepository;
import com.orbit.auth.application.port.out.RefreshSessionRepository;
import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.AccountClosed;
import com.orbit.auth.domain.AccountId;
import com.orbit.shared.error.BusinessException;

/**
 * 탈퇴를 소유한 모듈이 부르는 계정 폐쇄. 호출자 트랜잭션에 참여해 탈퇴와 함께 확정·롤백되고, 같은 트랜잭션에서 그 계정의 기기 세션을 모두 지워 refresh
 * token을 끝내며, 커밋 뒤에만 그 계정의 Access Token을 폐기하도록 폐쇄 사실을 발행한다. 같은 계정의 동시 폐쇄는 계정 잠금으로 줄을 세워, 뒤에 온
 * 호출은 이미 닫힌 계정을 보고 DB를 바꾸지 않은 채 토큰 폐기만 다시 확정한다.
 */
@Service
public class AccountClosureService implements AccountClosure {

    private final AccountRepository accounts;
    private final ProviderUnlinkRepository providerUnlinks;
    private final RefreshSessionRepository refreshSessions;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AccountClosureService(
            AccountRepository accounts,
            ProviderUnlinkRepository providerUnlinks,
            RefreshSessionRepository refreshSessions,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.accounts = accounts;
        this.providerUnlinks = providerUnlinks;
        this.refreshSessions = refreshSessions;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void close(Long accountId) {
        Account account = accounts.findByIdForUpdate(new AccountId(accountId))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.ACCOUNT_NOT_FOUND));
        if (account.isClosed()) {
            // 앞선 폐쇄의 커밋 뒤 토큰 폐기가 실패했어도 다시 부르면 복구되도록 원래 폐쇄 시각으로 다시 알린다.
            eventPublisher.publishEvent(new AccountClosed(
                    account.id().orElseThrow(), account.closedAt().orElseThrow()));
            return;
        }
        Instant closedAt = clock.instant();
        providerUnlinks.saveAll(account.close(closedAt));
        accounts.update(account);
        refreshSessions.deleteAllByAccount(account.id().orElseThrow());
        eventPublisher.publishEvent(new AccountClosed(account.id().orElseThrow(), closedAt));
    }
}
