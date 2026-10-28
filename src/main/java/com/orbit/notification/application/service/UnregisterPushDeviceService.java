package com.orbit.notification.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.notification.application.error.NotificationErrorCode;
import com.orbit.notification.application.port.in.command.UnregisterPushDeviceUseCase;
import com.orbit.notification.application.port.in.command.dto.UnregisterPushDeviceCommand;
import com.orbit.notification.application.port.out.PushDeviceRepository;
import com.orbit.shared.error.BusinessException;

/**
 * 요청 계정의 앱 푸시 기기를 해제한다. 앱이 로그아웃하기 직전에 불러 그 기기로 이 계정의 알림이 더 가지 않게 한다. 없거나 다른 계정의 기기면 404(NOTIFICATION-005)다.
 * 이미 해제했거나, 같은 기기에서 다른 계정이 등록해 기기가 넘어간 경우도 여기에 든다.
 */
@Service
public class UnregisterPushDeviceService implements UnregisterPushDeviceUseCase {

    private final PushDeviceRepository pushDeviceRepository;

    public UnregisterPushDeviceService(PushDeviceRepository pushDeviceRepository) {
        this.pushDeviceRepository = pushDeviceRepository;
    }

    @Override
    @Transactional
    public void unregister(UnregisterPushDeviceCommand command) {
        if (!pushDeviceRepository.deleteOwnedBy(
                Objects.requireNonNull(command.pushDeviceId(), "pushDeviceId must not be null"),
                Objects.requireNonNull(command.accountId(), "accountId must not be null"))) {
            throw new BusinessException(NotificationErrorCode.PUSH_DEVICE_NOT_FOUND);
        }
    }
}
