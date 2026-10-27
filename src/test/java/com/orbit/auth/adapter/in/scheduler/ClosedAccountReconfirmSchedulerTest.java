package com.orbit.auth.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.orbit.auth.application.port.in.command.ReconfirmClosedAccountsUseCase;
import com.orbit.shared.internal.config.SchedulingConfig;

@DisplayName("최근 폐쇄 계정 표시 주기 재확정")
class ClosedAccountReconfirmSchedulerTest {

    private final ReconfirmClosedAccountsUseCase useCase = mock(ReconfirmClosedAccountsUseCase.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class, ClosedAccountReconfirmScheduler.class)
            .withBean(ReconfirmClosedAccountsUseCase.class, () -> useCase);

    @Test
    @DisplayName("켜 두면 설정한 간격마다 최근 폐쇄 계정의 표시를 다시 확정한다")
    void reconfirmsPeriodicallyWhenEnabled() {
        runner.withPropertyValues(
                        "app.auth.closed-account.reconfirm-enabled=true",
                        "app.auth.closed-account.reconfirm-interval=PT0.1S")
                .run(context -> {
                    assertThat(context).hasSingleBean(ClosedAccountReconfirmScheduler.class);
                    verify(useCase, timeout(2000).atLeastOnce()).reconfirm();
                });
    }

    @Test
    @DisplayName("끄면 주기 작업을 등록하지 않는다")
    void staysOffWhenDisabled() {
        runner.withPropertyValues(
                        "app.auth.closed-account.reconfirm-enabled=false",
                        "app.auth.closed-account.reconfirm-interval=PT0.1S")
                .run(context -> assertThat(context).doesNotHaveBean(ClosedAccountReconfirmScheduler.class));
    }
}
