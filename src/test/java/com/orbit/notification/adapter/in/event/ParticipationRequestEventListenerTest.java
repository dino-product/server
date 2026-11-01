package com.orbit.notification.adapter.in.event;

import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.notification.application.port.in.command.CreateParticipationNotificationUseCase;
import com.orbit.notification.application.port.in.command.dto.CreateParticipationNotificationCommand;
import com.orbit.notification.domain.ParticipationType;
import com.orbit.organization.ParticipationRequestApproved;
import com.orbit.organization.ParticipationRequestRejected;

/** 이벤트 값을 Command로 옮기는지만 본다. 거절 사유 조회와 문구는 서비스 테스트가, 커밋 뒤 처리는 {@code NotificationModuleTest}가 확인한다. */
@ExtendWith(MockitoExtension.class)
@DisplayName("참여 요청 이벤트 리스너")
class ParticipationRequestEventListenerTest {

    private static final Instant DECIDED_AT = Instant.parse("2026-10-27T01:00:00Z");

    @Mock
    private CreateParticipationNotificationUseCase useCase;

    @Test
    @DisplayName("승인 이벤트는 요청자·희망·확정 유형을 담은 승인 Command가 된다")
    void convertsApprovedEvent() {
        ParticipationRequestEventListener listener = new ParticipationRequestEventListener(useCase);

        listener.on(new ParticipationRequestApproved(
                31L,
                1L,
                70L,
                ParticipationRequestApproved.Type.TECHNICIAN,
                ParticipationRequestApproved.Type.STAFF,
                DECIDED_AT));

        verify(useCase)
                .create(CreateParticipationNotificationCommand.approved(
                        31L, 1L, 70L, ParticipationType.TECHNICIAN, ParticipationType.STAFF, DECIDED_AT));
    }

    @Test
    @DisplayName("거절 이벤트는 요청자를 담은 거절 Command가 된다")
    void convertsRejectedEvent() {
        ParticipationRequestEventListener listener = new ParticipationRequestEventListener(useCase);

        listener.on(new ParticipationRequestRejected(32L, 1L, 71L, DECIDED_AT));

        verify(useCase).create(CreateParticipationNotificationCommand.rejected(32L, 1L, 71L, DECIDED_AT));
    }
}
