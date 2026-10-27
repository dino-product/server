package com.orbit.realtime.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.realtime.domain.RealtimeMessage;
import com.orbit.realtime.domain.RealtimeSignal;
import com.orbit.realtime.fake.RecordingRealtimeConnectionPort;

@DisplayName("실시간 신호 전달")
class DeliverRealtimeSignalServiceTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-26T01:00:00Z");

    private final RecordingRealtimeConnectionPort connections = new RecordingRealtimeConnectionPort();
    private final DeliverRealtimeSignalService service = new DeliverRealtimeSignalService(connections);

    @Test
    @DisplayName("작업 신호는 그 발주사의 연결로, 알림 신호는 그 계정의 연결로 보낸다")
    void routesWorkSignalsByOrganizationAndNotificationSignalsByAccount() {
        service.deliver(new RealtimeSignal.OrganizationWorksChanged(1L, List.of(10L, 11L), OCCURRED_AT));
        service.deliver(new RealtimeSignal.AccountNotificationCreated(7L, OCCURRED_AT));
        service.deliver(new RealtimeSignal.AccountNotificationRead(7L, OCCURRED_AT));

        assertThat(connections.sent())
                .containsExactly(
                        "organization 1 " + new RealtimeMessage.WorkChanged(List.of(10L, 11L), OCCURRED_AT),
                        "account 7 " + new RealtimeMessage.NotificationCreated(OCCURRED_AT),
                        "account 7 " + new RealtimeMessage.NotificationRead(OCCURRED_AT));
    }
}
