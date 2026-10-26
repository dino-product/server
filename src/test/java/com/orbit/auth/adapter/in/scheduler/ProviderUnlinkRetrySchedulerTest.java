package com.orbit.auth.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.orbit.auth.application.port.in.command.RetryProviderUnlinksUseCase;
import com.orbit.shared.internal.config.SchedulingConfig;

@DisplayName("외부 연결 해제 주기 재시도")
class ProviderUnlinkRetrySchedulerTest {

    private final RetryProviderUnlinksUseCase useCase = mock(RetryProviderUnlinksUseCase.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class, ProviderUnlinkRetryScheduler.class)
            .withBean(RetryProviderUnlinksUseCase.class, () -> useCase);

    @Test
    @DisplayName("켜 두면 설정한 간격마다 기한이 지난 대기를 다시 처리한다")
    void retriesPeriodicallyWhenEnabled() {
        runner.withPropertyValues(
                        "app.auth.provider-unlink.retry-enabled=true", "app.auth.provider-unlink.retry-interval=PT0.1S")
                .run(context -> {
                    assertThat(context).hasSingleBean(ProviderUnlinkRetryScheduler.class);
                    verify(useCase, timeout(2000).atLeastOnce()).retryDue();
                });
    }

    @Test
    @DisplayName("끄면 주기 작업을 등록하지 않는다")
    void staysOffWhenDisabled() {
        runner.withPropertyValues(
                        "app.auth.provider-unlink.retry-enabled=false",
                        "app.auth.provider-unlink.retry-interval=PT0.1S")
                .run(context -> assertThat(context).doesNotHaveBean(ProviderUnlinkRetryScheduler.class));
    }
}
