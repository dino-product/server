package com.orbit.notification.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.notification.application.port.in.command.CreateParticipationNotificationUseCase;
import com.orbit.notification.application.port.in.command.dto.CreateParticipationNotificationCommand;
import com.orbit.notification.application.port.out.NotificationRepository;
import com.orbit.notification.application.port.out.PushNotificationPort;
import com.orbit.notification.domain.Notification;
import com.orbit.notification.domain.NotificationMessage;
import com.orbit.organization.ParticipationRequestLookup;

/**
 * 참여 요청 결과 알림을 요청자 계정의 알림센터에 안 읽음으로 남기고 푸시한다. 요청자는 아직 그 발주사의 구성원이 아닐 수 있어 구성원인지 확인하지 않고, 알림 설정
 * 분류가 없어 설정과 무관하게 푸시한다. 거절 사유는 이벤트에 싣지 않으므로 organization 공개 조회로 읽는다. 같은 참여 요청의 같은 종류 알림이 이미 있으면(이벤트
 * 재전송) 다시 만들지도 푸시하지도 않는다. 같은 결과가 동시에 처리되면 한쪽은 저장소의 유일 제약으로 실패하고 재전송 때 건너뛴다.
 */
@Service
public class CreateParticipationNotificationService implements CreateParticipationNotificationUseCase {

    private final ParticipationRequestLookup participationRequestLookup;
    private final NotificationRepository notificationRepository;
    private final PushNotificationPort pushNotificationPort;

    public CreateParticipationNotificationService(
            ParticipationRequestLookup participationRequestLookup,
            NotificationRepository notificationRepository,
            PushNotificationPort pushNotificationPort) {
        this.participationRequestLookup = participationRequestLookup;
        this.notificationRepository = notificationRepository;
        this.pushNotificationPort = pushNotificationPort;
    }

    @Override
    @Transactional
    public void create(CreateParticipationNotificationCommand command) {
        Objects.requireNonNull(command.type(), "type must not be null");
        if (notificationRepository.existsReceivedForParticipationRequest(
                command.applicantAccountId(), command.type(), command.participationRequestId())) {
            return;
        }
        Notification saved = notificationRepository.save(Notification.createForParticipationRequest(
                command.applicantAccountId(),
                command.organizationId(),
                command.type(),
                command.participationRequestId(),
                message(command),
                command.occurredAt()));
        pushNotificationPort.send(saved);
    }

    private String message(CreateParticipationNotificationCommand command) {
        return switch (command.type()) {
            case PARTICIPATION_REQUEST_APPROVED ->
                NotificationMessage.composeParticipationApproved(command.desiredType(), command.confirmedType());
            case PARTICIPATION_REQUEST_REJECTED ->
                NotificationMessage.composeParticipationRejected(participationRequestLookup
                        .findRejectReason(command.participationRequestId())
                        .orElse(null));
            default -> throw new IllegalArgumentException(command.type() + " is not a participation request result");
        };
    }
}
