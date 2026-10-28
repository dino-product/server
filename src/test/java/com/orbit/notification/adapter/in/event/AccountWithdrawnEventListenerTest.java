package com.orbit.notification.adapter.in.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.notification.application.port.in.command.RemoveWithdrawnAccountPushDevicesUseCase;
import com.orbit.notification.application.port.in.command.dto.RemoveWithdrawnAccountPushDevicesCommand;
import com.orbit.profile.AccountWithdrawn;

@DisplayName("탈퇴 이벤트 리스너")
class AccountWithdrawnEventListenerTest {

    @Test
    @DisplayName("탈퇴한 계정의 앱 푸시 기기 삭제를 요청한다")
    void requestsRemovalOfWithdrawnAccountDevices() {
        List<RemoveWithdrawnAccountPushDevicesCommand> commands = new ArrayList<>();
        RemoveWithdrawnAccountPushDevicesUseCase useCase = commands::add;

        new AccountWithdrawnEventListener(useCase).on(new AccountWithdrawn(7L, Instant.parse("2026-10-28T01:00:00Z")));

        assertThat(commands).containsExactly(new RemoveWithdrawnAccountPushDevicesCommand(7L));
    }
}
