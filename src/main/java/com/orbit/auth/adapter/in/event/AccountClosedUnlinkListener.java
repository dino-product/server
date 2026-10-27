package com.orbit.auth.adapter.in.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.orbit.auth.application.port.in.command.UnlinkAccountProvidersUseCase;
import com.orbit.auth.application.port.in.command.dto.UnlinkAccountProvidersCommand;
import com.orbit.auth.domain.AccountClosed;

/**
 * 계정 폐쇄가 커밋되면 다른 스레드에서 그 계정의 외부 연결 해제를 한 번 시도한다. 롤백된 폐쇄는 받지 않는다. 외부 HTTP를 기다리는 동안 DB 커넥션을 잡지 않도록
 * 트랜잭션을 여는 {@code @ApplicationModuleListener} 대신 트랜잭션 없는 비동기 리스너로 둔다. 실패한 대기는 주기 재시도가 이어 받는다.
 */
@Component
public class AccountClosedUnlinkListener {

    private final UnlinkAccountProvidersUseCase useCase;

    public AccountClosedUnlinkListener(UnlinkAccountProvidersUseCase useCase) {
        this.useCase = useCase;
    }

    @Async
    @TransactionalEventListener
    public void on(AccountClosed event) {
        useCase.unlink(new UnlinkAccountProvidersCommand(event.accountId().value()));
    }
}
