package com.orbit.notification.adapter.in.event;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.orbit.notification.application.port.in.command.RemoveWithdrawnAccountPushDevicesUseCase;
import com.orbit.notification.application.port.in.command.dto.RemoveWithdrawnAccountPushDevicesCommand;
import com.orbit.profile.AccountWithdrawn;

/**
 * profile의 계정 탈퇴가 커밋되면 다른 스레드·트랜잭션에서 그 계정의 앱 푸시 기기를 지운다. 롤백된 탈퇴는 받지 않고, 실패한 처리는 이벤트 발행 기록이 다시 보낸다.
 */
@Component
class AccountWithdrawnEventListener {

    private final RemoveWithdrawnAccountPushDevicesUseCase useCase;

    AccountWithdrawnEventListener(RemoveWithdrawnAccountPushDevicesUseCase useCase) {
        this.useCase = useCase;
    }

    @ApplicationModuleListener
    public void on(AccountWithdrawn event) {
        useCase.remove(new RemoveWithdrawnAccountPushDevicesCommand(event.accountId()));
    }
}
