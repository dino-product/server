package com.orbit.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.notification.application.error.NotificationErrorCode;
import com.orbit.notification.application.port.in.command.dto.UnregisterPushDeviceCommand;
import com.orbit.notification.application.service.fake.InMemoryPushDeviceRepository;
import com.orbit.notification.domain.PushDevice;
import com.orbit.notification.domain.PushPlatform;
import com.orbit.shared.error.BusinessException;

@DisplayName("앱 푸시 기기 해제")
class UnregisterPushDeviceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T01:00:00Z");
    private static final String TOKEN = "fcm-token-abcdefghijklmnopqrstuvwxyz";

    private final InMemoryPushDeviceRepository devices = new InMemoryPushDeviceRepository();
    private final UnregisterPushDeviceService service = new UnregisterPushDeviceService(devices);

    @Test
    @DisplayName("본인 기기를 해제하면 기기가 사라진다")
    void unregistersOwnDevice() {
        Long deviceId = registeredDeviceOf(7L);

        service.unregister(new UnregisterPushDeviceCommand(7L, deviceId));

        assertThat(devices.findByToken(TOKEN)).isEmpty();
    }

    @Test
    @DisplayName("다른 계정의 기기나 없는 기기는 NOTIFICATION-005이고 기기를 남긴다")
    void rejectsOthersOrMissingDevice() {
        Long deviceId = registeredDeviceOf(7L);

        assertThatThrownBy(() -> service.unregister(new UnregisterPushDeviceCommand(8L, deviceId)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(NotificationErrorCode.PUSH_DEVICE_NOT_FOUND));
        assertThatThrownBy(() -> service.unregister(new UnregisterPushDeviceCommand(7L, deviceId + 1)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(NotificationErrorCode.PUSH_DEVICE_NOT_FOUND));

        assertThat(devices.findByToken(TOKEN)).isPresent();
    }

    private Long registeredDeviceOf(Long accountId) {
        return devices.register(PushDevice.register(accountId, PushPlatform.IOS, TOKEN, NOW))
                .id()
                .orElseThrow();
    }
}
