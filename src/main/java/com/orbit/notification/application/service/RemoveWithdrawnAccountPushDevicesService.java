package com.orbit.notification.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.notification.application.port.in.command.RemoveWithdrawnAccountPushDevicesUseCase;
import com.orbit.notification.application.port.in.command.dto.RemoveWithdrawnAccountPushDevicesCommand;
import com.orbit.notification.application.port.out.PushDeviceRepository;

/**
 * 탈퇴한 계정의 앱 푸시 기기를 모두 지운다. 탈퇴한 계정은 활성 소속·계약이 없어 새 알림을 받지 않지만, 기기 토큰은 그 기기로 푸시를 보낼 수 있는 값이라 남기지
 * 않는다. 같은 탈퇴를 다시 받아도 지울 기기가 없을 뿐 결과가 같다.
 */
@Service
public class RemoveWithdrawnAccountPushDevicesService implements RemoveWithdrawnAccountPushDevicesUseCase {

    private final PushDeviceRepository pushDeviceRepository;

    public RemoveWithdrawnAccountPushDevicesService(PushDeviceRepository pushDeviceRepository) {
        this.pushDeviceRepository = pushDeviceRepository;
    }

    @Override
    @Transactional
    public void remove(RemoveWithdrawnAccountPushDevicesCommand command) {
        pushDeviceRepository.deleteByAccountId(
                Objects.requireNonNull(command.accountId(), "accountId must not be null"));
    }
}
