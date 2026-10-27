package com.orbit.auth.adapter.in.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.orbit.auth.application.port.in.command.RetryProviderUnlinksUseCase;

/** 다음 시도 시각이 지난 외부 연결 해제 대기를 주기적으로 다시 처리한다. 앞선 실행이 끝난 뒤 간격을 두고 다시 돈다. */
@Component
@ConditionalOnProperty(prefix = "app.auth.provider-unlink", name = "retry-enabled", havingValue = "true")
class ProviderUnlinkRetryScheduler {

    private final RetryProviderUnlinksUseCase useCase;

    ProviderUnlinkRetryScheduler(RetryProviderUnlinksUseCase useCase) {
        this.useCase = useCase;
    }

    @Scheduled(
            fixedDelayString = "${app.auth.provider-unlink.retry-interval}",
            initialDelayString = "${app.auth.provider-unlink.retry-interval}")
    void retryDue() {
        useCase.retryDue();
    }
}
