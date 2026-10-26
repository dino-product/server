package com.orbit.schedule.adapter.in.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.profile.AccountWithdrawn;
import com.orbit.schedule.application.port.in.command.ReleaseWithdrawnTechnicianWorksUseCase;
import com.orbit.schedule.application.port.in.command.dto.ReleaseWithdrawnTechnicianWorksCommand;

@DisplayName("탈퇴 이벤트 리스너")
class AccountWithdrawnListenerTest {

    @Test
    @DisplayName("탈퇴한 계정의 탈퇴 반환을 요청한다")
    void requestsWithdrawalReleaseOfAccount() {
        List<ReleaseWithdrawnTechnicianWorksCommand> commands = new ArrayList<>();
        ReleaseWithdrawnTechnicianWorksUseCase useCase = commands::add;

        new AccountWithdrawnListener(useCase).on(new AccountWithdrawn(7L, Instant.parse("2030-01-02T01:00:00Z")));

        assertThat(commands).containsExactly(new ReleaseWithdrawnTechnicianWorksCommand(7L));
    }
}
