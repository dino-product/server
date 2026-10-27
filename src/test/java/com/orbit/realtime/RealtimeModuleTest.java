package com.orbit.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.organization.OrganizationMemberLookup;
import com.orbit.organization.StaffMember;
import com.orbit.organization.TechnicianMember;
import com.orbit.realtime.application.port.in.command.OpenRealtimeStreamUseCase;
import com.orbit.realtime.application.port.in.command.dto.OpenRealtimeStreamCommand;
import com.orbit.realtime.domain.ClientType;
import com.orbit.realtime.domain.RealtimeMessage.Connected;
import com.orbit.realtime.fake.RecordingRealtimeChannel;
import com.orbit.schedule.WorkChanged;
import com.orbit.shared.error.BusinessException;
import com.orbit.support.SharedContainers;

/**
 * realtime만 띄워 연결 열기와 신호 전달(커밋 → Redis 채널 → 구독 → 연결)이 조립되는지 확인한다. organization 공개 계약은 조립 테스트가 띄우지 않으므로
 * 대체한다.
 */
@ApplicationModuleTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class RealtimeModuleTest implements SharedContainers {

    @MockitoBean
    private OrganizationMemberLookup memberLookup;

    @Autowired
    private OpenRealtimeStreamUseCase openRealtimeStreamUseCase;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void opensConnectionForStaffAndRejectsTechnician() {
        when(memberLookup.findActiveMember(9_301L, 300L)).thenReturn(Optional.of(new StaffMember(1L, 300L, false)));
        when(memberLookup.findActiveMember(9_302L, 300L)).thenReturn(Optional.of(new TechnicianMember(2L, 300L)));
        RecordingRealtimeChannel staff = new RecordingRealtimeChannel();

        openRealtimeStreamUseCase.open(new OpenRealtimeStreamCommand(9_301L, 300L, ClientType.DESKTOP_WEB, staff));

        await().atMost(Duration.ofSeconds(5)).until(() -> staff.sent().contains(new Connected()));
        assertThatThrownBy(() -> openRealtimeStreamUseCase.open(new OpenRealtimeStreamCommand(
                        9_302L, 300L, ClientType.DESKTOP_WEB, new RecordingRealtimeChannel())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deliversCommittedWorkChangeOnlyToOrganizationConnections(Scenario scenario) {
        when(memberLookup.findActiveMember(9_303L, 301L)).thenReturn(Optional.of(new StaffMember(3L, 301L, false)));
        RecordingRealtimeChannel staff = new RecordingRealtimeChannel();
        openRealtimeStreamUseCase.open(new OpenRealtimeStreamCommand(9_303L, 301L, ClientType.DESKTOP_WEB, staff));
        Instant occurredAt = Instant.parse("2026-10-26T01:00:00Z");
        // 롤백한 변경을 먼저 발행한다. 신호는 한 줄로 차례로 가므로, 뒤에 커밋한 변경을 받았을 때 앞의 것이 없으면 보내지 않은 것이다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(new WorkChanged(301L, List.of(40L), occurredAt));
            status.setRollbackOnly();
        });

        scenario.publish(new WorkChanged(301L, List.of(41L), occurredAt))
                .andWaitForStateChange(staff::sent, sent -> sent.size() > 1)
                .andVerify(sent -> assertThat(sent)
                        .containsExactly(
                                new Connected(),
                                new com.orbit.realtime.domain.RealtimeMessage.WorkChanged(List.of(41L), occurredAt)));
    }
}
