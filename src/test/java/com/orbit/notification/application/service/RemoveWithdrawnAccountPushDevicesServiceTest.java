package com.orbit.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.notification.application.port.in.command.dto.RemoveWithdrawnAccountPushDevicesCommand;
import com.orbit.notification.application.service.fake.InMemoryPushDeviceRepository;
import com.orbit.notification.domain.PushDevice;
import com.orbit.notification.domain.PushPlatform;

@DisplayName("탈퇴한 계정의 앱 푸시 기기 삭제")
class RemoveWithdrawnAccountPushDevicesServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T01:00:00Z");

    private final InMemoryPushDeviceRepository devices = new InMemoryPushDeviceRepository();
    private final RemoveWithdrawnAccountPushDevicesService service =
            new RemoveWithdrawnAccountPushDevicesService(devices);

    @Test
    @DisplayName("탈퇴한 계정의 기기만 모두 지우고 같은 탈퇴를 다시 받아도 결과가 같다")
    void removesAllDevicesOfWithdrawnAccountOnly() {
        devices.register(PushDevice.register(7L, PushPlatform.IOS, "token-phone", NOW));
        devices.register(PushDevice.register(7L, PushPlatform.ANDROID, "token-tablet", NOW));
        devices.register(PushDevice.register(8L, PushPlatform.ANDROID, "token-other", NOW));

        service.remove(new RemoveWithdrawnAccountPushDevicesCommand(7L));
        service.remove(new RemoveWithdrawnAccountPushDevicesCommand(7L));

        assertThat(devices.listByAccountId(7L)).isEmpty();
        assertThat(devices.listByAccountId(8L)).extracting(PushDevice::token).containsExactly("token-other");
    }
}
