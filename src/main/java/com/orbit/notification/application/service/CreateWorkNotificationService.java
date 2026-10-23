package com.orbit.notification.application.service;

import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.notification.application.port.in.command.CreateWorkNotificationUseCase;
import com.orbit.notification.application.port.in.command.dto.CreateWorkNotificationCommand;
import com.orbit.notification.application.port.out.NotificationRepository;
import com.orbit.notification.application.port.out.NotificationSettingRepository;
import com.orbit.notification.application.port.out.PushNotificationPort;
import com.orbit.notification.domain.Notification;
import com.orbit.notification.domain.NotificationAudience;
import com.orbit.notification.domain.NotificationMessage;
import com.orbit.notification.domain.NotificationSetting;
import com.orbit.notification.domain.NotificationType;
import com.orbit.organization.OrganizationAccountLookup;

/**
 * 작업 알림을 받는 사람의 알림센터에 안 읽음으로 남기고, 기사 알림이면 그 계정의 알림 설정이 허락할 때 푸시한다. 받는 계정이 없으면(비활성·없는 기사 계약이나 소속,
 * 담당 기사·등록자 없음) 아무것도 남기지 않는다. 수락한 작업에만 보내는 종류는 받는 기사가 수락하지 않았던 작업이면 만들지 않는다. 거절이면 등록자 알림과 함께,
 * 거절한 기사가 그 작업에 대해 거절 시점까지 받은 기사 알림을 숨긴다(직원에서 기사로 역할이 바뀐 계정이 받은 사무실 알림은 숨기지 않는다). 같은 일이 두 번
 * 들어오면(이벤트 재전송) 이미 남긴 같은 일(받는 계정·종류·작업·일어난 시각)의 알림을 찾아 다시 만들지도 푸시하지도 않는다. 같은 일이 동시에 두 번 처리되면 한쪽은
 * 저장소의 유일 제약으로 실패하고 재전송 때 건너뛴다. 이벤트마다 따로 처리돼 순서가 보장되지 않으므로, 배정 알림 처리가 그 뒤의 거절 처리보다 늦게 커밋되면 그
 * 배정 알림은 숨기지 못한다.
 */
@Service
public class CreateWorkNotificationService implements CreateWorkNotificationUseCase {

    private final OrganizationAccountLookup accountLookup;
    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository settingRepository;
    private final PushNotificationPort pushNotificationPort;

    public CreateWorkNotificationService(
            OrganizationAccountLookup accountLookup,
            NotificationRepository notificationRepository,
            NotificationSettingRepository settingRepository,
            PushNotificationPort pushNotificationPort) {
        this.accountLookup = accountLookup;
        this.notificationRepository = notificationRepository;
        this.settingRepository = settingRepository;
        this.pushNotificationPort = pushNotificationPort;
    }

    @Override
    @Transactional
    public void create(CreateWorkNotificationCommand command) {
        Objects.requireNonNull(command.type(), "type must not be null");
        Objects.requireNonNull(command.occurredAt(), "occurredAt must not be null");
        if (command.type() == NotificationType.WORK_REJECTED) {
            hideRejectedWork(command);
        }
        if (command.type().onlyForAcceptedWork() && !command.technicianAccepted()) {
            return;
        }
        recipientOf(command).ifPresent(accountId -> notify(accountId, command));
    }

    private Optional<Long> recipientOf(CreateWorkNotificationCommand command) {
        return switch (command.type().audience()) {
            case TECHNICIAN -> technicianAccount(command);
            case OFFICE ->
                command.registrarMembershipId() == null
                        ? Optional.empty()
                        : accountLookup.findActiveStaffAccountId(
                                command.organizationId(), command.registrarMembershipId());
        };
    }

    private Optional<Long> technicianAccount(CreateWorkNotificationCommand command) {
        if (command.technicianId() == null) {
            return Optional.empty();
        }
        return accountLookup.findActiveTechnicianAccountId(command.organizationId(), command.technicianId());
    }

    private void notify(Long accountId, CreateWorkNotificationCommand command) {
        if (notificationRepository.existsReceived(accountId, command.type(), command.workId(), command.occurredAt())) {
            return;
        }
        String message = NotificationMessage.compose(
                command.type(),
                command.workName(),
                command.scheduledStartAt(),
                command.rejectionReasonCode(),
                command.rejectionReasonNote());
        Notification saved = notificationRepository.save(Notification.create(
                accountId, command.organizationId(), command.type(), command.workId(), message, command.occurredAt()));
        if (saved.type().pushes() && settingOf(accountId).allowsPush(saved.type())) {
            pushNotificationPort.send(saved);
        }
    }

    private NotificationSetting settingOf(Long accountId) {
        return settingRepository.findByAccountId(accountId).orElseGet(() -> NotificationSetting.defaults(accountId));
    }

    private void hideRejectedWork(CreateWorkNotificationCommand command) {
        Optional<Long> technicianAccountId = technicianAccount(command);
        if (technicianAccountId.isEmpty() || command.workId() == null) {
            return;
        }
        for (Notification notification :
                notificationRepository.listReceivedForWork(technicianAccountId.get(), command.workId())) {
            if (notification.type().audience() == NotificationAudience.TECHNICIAN
                    && notification.hideIfNotifiedBy(command.occurredAt())) {
                notificationRepository.save(notification);
            }
        }
    }
}
