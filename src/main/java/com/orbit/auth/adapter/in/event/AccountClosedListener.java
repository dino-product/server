package com.orbit.auth.adapter.in.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.orbit.auth.application.port.in.command.RevokeAccountTokensUseCase;
import com.orbit.auth.application.port.in.command.dto.RevokeAccountTokensCommand;
import com.orbit.auth.domain.AccountClosed;

/**
 * 계정 폐쇄가 커밋되면 같은 스레드에서 그 계정의 토큰을 폐기해, 탈퇴 응답을 받은 뒤에는 이전 토큰이 인증되지 않게 한다. 롤백된 폐쇄는 받지 않는다. 폐기가
 * 실패해도 이미 커밋된 폐쇄는 되돌리지 않으며 Spring이 오류를 기록한다.
 */
@Component
public class AccountClosedListener {

    private final RevokeAccountTokensUseCase useCase;

    public AccountClosedListener(RevokeAccountTokensUseCase useCase) {
        this.useCase = useCase;
    }

    @TransactionalEventListener
    public void on(AccountClosed event) {
        useCase.revoke(new RevokeAccountTokensCommand(event.accountId().value(), event.closedAt()));
    }
}
