package com.orbit.realtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.organization.OrganizationMemberLookup;
import com.orbit.organization.StaffMember;
import com.orbit.organization.TechnicianMember;
import com.orbit.realtime.application.port.in.command.OpenRealtimeStreamUseCase;
import com.orbit.realtime.application.port.in.command.dto.OpenRealtimeStreamCommand;
import com.orbit.realtime.domain.ClientType;
import com.orbit.realtime.domain.RealtimeMessage.Connected;
import com.orbit.realtime.fake.RecordingRealtimeChannel;
import com.orbit.shared.error.BusinessException;
import com.orbit.support.SharedContainers;

/** realtime만 띄워 연결 열기가 설정·시각·지표와 함께 조립되는지 확인한다. organization 공개 계약은 조립 테스트가 띄우지 않으므로 대체한다. */
@ApplicationModuleTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class RealtimeModuleTest implements SharedContainers {

    @MockitoBean
    private OrganizationMemberLookup memberLookup;

    @Autowired
    private OpenRealtimeStreamUseCase openRealtimeStreamUseCase;

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
}
