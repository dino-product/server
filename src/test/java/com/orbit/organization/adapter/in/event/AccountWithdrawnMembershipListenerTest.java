package com.orbit.organization.adapter.in.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.organization.application.port.in.command.DeactivateWithdrawnAccountUseCase;
import com.orbit.organization.application.port.in.command.dto.DeactivateWithdrawnAccountCommand;
import com.orbit.profile.AccountWithdrawn;

@DisplayName("탈퇴 이벤트 리스너")
class AccountWithdrawnMembershipListenerTest {

    @Test
    @DisplayName("탈퇴한 계정의 소속 비활성화를 요청한다")
    void requestsDeactivationOfWithdrawnAccount() {
        List<DeactivateWithdrawnAccountCommand> commands = new ArrayList<>();
        DeactivateWithdrawnAccountUseCase useCase = commands::add;

        new AccountWithdrawnMembershipListener(useCase)
                .on(new AccountWithdrawn(7L, Instant.parse("2026-10-21T01:00:00Z")));

        assertThat(commands).containsExactly(new DeactivateWithdrawnAccountCommand(7L));
    }
}
