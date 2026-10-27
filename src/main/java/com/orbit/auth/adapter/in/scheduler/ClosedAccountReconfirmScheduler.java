package com.orbit.auth.adapter.in.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.orbit.auth.application.port.in.command.ReconfirmClosedAccountsUseCase;

/** 최근 폐쇄 계정의 폐쇄 표시를 주기적으로 다시 확정한다. 앞선 실행이 끝난 뒤 간격을 두고 다시 돈다. */
@Component
@ConditionalOnProperty(prefix = "app.auth.closed-account", name = "reconfirm-enabled", havingValue = "true")
class ClosedAccountReconfirmScheduler {

    private final ReconfirmClosedAccountsUseCase useCase;

    ClosedAccountReconfirmScheduler(ReconfirmClosedAccountsUseCase useCase) {
        this.useCase = useCase;
    }

    @Scheduled(
            fixedDelayString = "${app.auth.closed-account.reconfirm-interval}",
            initialDelayString = "${app.auth.closed-account.reconfirm-interval}")
    void reconfirm() {
        useCase.reconfirm();
    }
}
