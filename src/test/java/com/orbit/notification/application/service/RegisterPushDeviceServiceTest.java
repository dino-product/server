package com.orbit.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.notification.application.port.in.command.dto.RegisterPushDeviceCommand;
import com.orbit.notification.application.port.in.command.dto.RegisteredPushDeviceInfo;
import com.orbit.notification.application.service.fake.InMemoryPushDeviceRepository;
import com.orbit.notification.domain.PushPlatform;

@DisplayName("앱 푸시 기기 등록")
class RegisterPushDeviceServiceTest {

    private static final Instant FIRST = Instant.parse("2026-10-28T01:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-10-28T02:00:00Z");
    private static final String TOKEN = "fcm-token-abcdefghijklmnopqrstuvwxyz";

    private final InMemoryPushDeviceRepository devices = new InMemoryPushDeviceRepository();

    @Test
    @DisplayName("새 토큰이면 요청 계정의 기기로 등록하고 식별자를 돌려준다")
    void registersNewDeviceForRequester() {
        RegisteredPushDeviceInfo registered =
                serviceAt(FIRST).register(new RegisterPushDeviceCommand(7L, PushPlatform.ANDROID, TOKEN));

        assertThat(devices.findByToken(TOKEN)).hasValueSatisfying(device -> {
            assertThat(device.id()).contains(registered.pushDeviceId());
            assertThat(device.accountId()).isEqualTo(7L);
            assertThat(device.platform()).isEqualTo(PushPlatform.ANDROID);
            assertThat(device.registeredAt()).isEqualTo(FIRST);
        });
    }

    @Test
    @DisplayName("같은 계정이 같은 토큰을 다시 등록하면 같은 기기의 등록 시각만 새로 한다")
    void reregistersSameTokenAsSameDevice() {
        RegisteredPushDeviceInfo first =
                serviceAt(FIRST).register(new RegisterPushDeviceCommand(7L, PushPlatform.IOS, TOKEN));

        RegisteredPushDeviceInfo second =
                serviceAt(SECOND).register(new RegisterPushDeviceCommand(7L, PushPlatform.IOS, TOKEN));

        assertThat(second.pushDeviceId()).isEqualTo(first.pushDeviceId());
        assertThat(devices.count()).isEqualTo(1);
        assertThat(devices.findByToken(TOKEN).orElseThrow().registeredAt()).isEqualTo(SECOND);
    }

    @Test
    @DisplayName("같은 기기에서 다른 계정이 등록하면 기기가 그 계정으로 넘어간다")
    void movesDeviceToAccountThatRegisteredLast() {
        RegisteredPushDeviceInfo previous =
                serviceAt(FIRST).register(new RegisterPushDeviceCommand(7L, PushPlatform.ANDROID, TOKEN));

        RegisteredPushDeviceInfo moved =
                serviceAt(SECOND).register(new RegisterPushDeviceCommand(8L, PushPlatform.ANDROID, TOKEN));

        assertThat(moved.pushDeviceId()).isEqualTo(previous.pushDeviceId());
        assertThat(devices.findByToken(TOKEN)).hasValueSatisfying(device -> {
            assertThat(device.isOwnedBy(8L)).isTrue();
            assertThat(device.isOwnedBy(7L)).isFalse();
        });
    }

    private RegisterPushDeviceService serviceAt(Instant now) {
        return new RegisterPushDeviceService(devices, Clock.fixed(now, ZoneOffset.UTC));
    }
}
