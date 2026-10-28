package com.orbit.notification.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.notification.application.port.in.command.RemoveWithdrawnAccountPushDevicesUseCase;
import com.orbit.notification.application.port.in.command.dto.RemoveWithdrawnAccountPushDevicesCommand;
import com.orbit.notification.application.port.out.PushDeviceRepository;

/**
 * 탈퇴한 계정의 앱 푸시 기기를 모두 지운다. 기기 토큰은 그 기기로 푸시를 보낼 수 있는 값이라 탈퇴 뒤 남기지 않고, 탈퇴한 계정에 알림이 생겨도 그 기기로 가지
 * 않게 한다. 같은 탈퇴를 다시 받아도 지울 기기가 없을 뿐 결과가 같다. 탈퇴가 커밋되기 직전에 인증을 통과한 기기 등록이 이 삭제보다 늦게 커밋되면 그 기기는 남는다.
 * 드문 경합이라 받아들이며, 같은 기기에서 다른 계정이 등록하면 그 계정으로 넘어간다.
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
