package com.orbit.auth.adapter.in.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.orbit.auth.application.port.in.command.RevokeAccountTokensUseCase;
import com.orbit.auth.application.port.in.command.dto.RevokeAccountTokensCommand;
import com.orbit.auth.domain.AccountClosed;

/**
 * 계정 폐쇄가 커밋되면 그 계정의 토큰을 폐기한다. 롤백된 폐쇄는 받지 않는다. 커밋 뒤 소비는 보통 다른 스레드의 {@code @ApplicationModuleListener}로
 * 하지만, 탈퇴 응답을 받은 클라이언트의 이전 토큰이 바로 거부돼야 하므로 응답 전에 같은 스레드에서 처리한다. 폐기가 실패해도 커밋된 폐쇄는 되돌리지 않고
 * Spring이 오류를 기록하며, 같은 계정을 다시 닫으면 복구된다.
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
