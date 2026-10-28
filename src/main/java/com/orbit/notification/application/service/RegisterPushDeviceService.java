package com.orbit.notification.application.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.notification.application.port.in.command.RegisterPushDeviceUseCase;
import com.orbit.notification.application.port.in.command.dto.RegisterPushDeviceCommand;
import com.orbit.notification.application.port.in.command.dto.RegisteredPushDeviceInfo;
import com.orbit.notification.application.port.out.PushDeviceRepository;
import com.orbit.notification.domain.PushDevice;

/**
 * 요청 계정의 앱 푸시 기기를 등록한다. 앱은 시작할 때와 토큰이 바뀔 때마다 부르므로 같은 토큰을 다시 등록하면 같은 기기의 등록 시각만 새로 한다. 같은 기기에서 다른
 * 계정으로 로그인해 등록하면 기기가 그 계정으로 넘어가 이전 계정의 알림은 그 기기로 가지 않는다. 역할은 보지 않는다. 소속 전 신청자도 참여 요청 결과 푸시를 받기
 * 때문이다.
 */
@Service
public class RegisterPushDeviceService implements RegisterPushDeviceUseCase {

    private final PushDeviceRepository pushDeviceRepository;
    private final Clock clock;

    public RegisterPushDeviceService(PushDeviceRepository pushDeviceRepository, Clock clock) {
        this.pushDeviceRepository = pushDeviceRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RegisteredPushDeviceInfo register(RegisterPushDeviceCommand command) {
        PushDevice device = pushDeviceRepository.register(
                PushDevice.register(command.accountId(), command.platform(), command.token(), clock.instant()));
        return new RegisteredPushDeviceInfo(device.id().orElseThrow());
    }
}
