package com.orbit.notification.adapter.in.event;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.orbit.notification.application.port.in.command.CreateParticipationNotificationUseCase;
import com.orbit.notification.application.port.in.command.dto.CreateParticipationNotificationCommand;
import com.orbit.notification.domain.ParticipationType;
import com.orbit.organization.ParticipationRequestApproved;
import com.orbit.organization.ParticipationRequestRejected;

/**
 * organization의 참여 요청 승인·거절 이벤트를 요청자 알림 Command로 바꾼다. organization 커밋 뒤 다른 스레드·트랜잭션에서 실행되므로 실패해도 승인·거절은
 * 되돌아가지 않고, 실패한 처리는 이벤트 발행 기록이 다시 보낸다.
 */
@Component
public class ParticipationRequestEventListener {

    private final CreateParticipationNotificationUseCase useCase;

    public ParticipationRequestEventListener(CreateParticipationNotificationUseCase useCase) {
        this.useCase = useCase;
    }

    @ApplicationModuleListener
    public void on(ParticipationRequestApproved event) {
        useCase.create(CreateParticipationNotificationCommand.approved(
                event.requestId(),
                event.organizationId(),
                event.accountId(),
                type(event.desiredType()),
                type(event.confirmedType()),
                event.occurredAt()));
    }

    @ApplicationModuleListener
    public void on(ParticipationRequestRejected event) {
        useCase.create(CreateParticipationNotificationCommand.rejected(
                event.requestId(), event.organizationId(), event.accountId(), event.occurredAt()));
    }

    private static ParticipationType type(ParticipationRequestApproved.Type type) {
        return switch (type) {
            case STAFF -> ParticipationType.STAFF;
            case TECHNICIAN -> ParticipationType.TECHNICIAN;
        };
    }
}
