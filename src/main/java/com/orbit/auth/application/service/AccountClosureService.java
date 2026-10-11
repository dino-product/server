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
import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.AccountClosed;
import com.orbit.auth.domain.AccountId;
import com.orbit.shared.error.BusinessException;

/**
 * 탈퇴를 소유한 모듈이 부르는 계정 폐쇄. 호출자 트랜잭션에 참여해 탈퇴와 함께 확정·롤백되고, 커밋 뒤에만 그 계정의 Access Token을 폐기하도록 폐쇄 사실을
 * 발행한다. 같은 계정의 동시 폐쇄는 계정 잠금으로 줄을 세워, 뒤에 온 호출은 이미 닫힌 계정을 보고 아무것도 바꾸지 않는다.
 */
@Service
public class AccountClosureService implements AccountClosure {

    private final AccountRepository accounts;
    private final ProviderUnlinkRepository providerUnlinks;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AccountClosureService(
            AccountRepository accounts,
            ProviderUnlinkRepository providerUnlinks,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.accounts = accounts;
        this.providerUnlinks = providerUnlinks;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void close(Long accountId) {
        Account account = accounts.findByIdForUpdate(new AccountId(accountId))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.ACCOUNT_NOT_FOUND));
        if (account.isClosed()) {
            return;
        }
        Instant closedAt = clock.instant();
        providerUnlinks.saveAll(account.close(closedAt));
        accounts.update(account);
        eventPublisher.publishEvent(new AccountClosed(account.id().orElseThrow(), closedAt));
    }
}
