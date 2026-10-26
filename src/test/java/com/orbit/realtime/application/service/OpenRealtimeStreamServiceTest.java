package com.orbit.realtime.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.organization.OrganizationMember;
import com.orbit.organization.StaffMember;
import com.orbit.organization.TechnicianMember;
import com.orbit.realtime.application.error.RealtimeErrorCode;
import com.orbit.realtime.application.port.in.command.dto.OpenRealtimeStreamCommand;
import com.orbit.realtime.domain.ClientType;
import com.orbit.realtime.fake.RecordingRealtimeChannel;
import com.orbit.realtime.fake.RecordingRealtimeConnectionPort;
import com.orbit.shared.error.BusinessException;

@DisplayName("실시간 연결 열기")
class OpenRealtimeStreamServiceTest {

    private static final Long ORGANIZATION_ID = 1L;
    private static final Long STAFF_ACCOUNT_ID = 10L;
    private static final Long OWNER_ACCOUNT_ID = 11L;
    private static final Long TECHNICIAN_ACCOUNT_ID = 20L;

    private final Map<List<Long>, OrganizationMember> members = new HashMap<>(Map.of(
            List.of(STAFF_ACCOUNT_ID, ORGANIZATION_ID), new StaffMember(100L, ORGANIZATION_ID, false),
            List.of(OWNER_ACCOUNT_ID, ORGANIZATION_ID), new StaffMember(101L, ORGANIZATION_ID, true),
            List.of(TECHNICIAN_ACCOUNT_ID, ORGANIZATION_ID), new TechnicianMember(200L, ORGANIZATION_ID)));
    private final RecordingRealtimeConnectionPort connections = new RecordingRealtimeConnectionPort();

    private final OpenRealtimeStreamService service = new OpenRealtimeStreamService(
            (accountId, organizationId) -> Optional.ofNullable(members.get(List.of(accountId, organizationId))),
            connections);

    @Test
    @DisplayName("발주사의 활성 직원과 총관리자는 연결을 등록한다")
    void registersStaffMembers() {
        service.open(command(STAFF_ACCOUNT_ID, ORGANIZATION_ID));
        service.open(command(OWNER_ACCOUNT_ID, ORGANIZATION_ID));

        assertThat(connections.registeredAccounts()).containsExactly(STAFF_ACCOUNT_ID, OWNER_ACCOUNT_ID);
    }

    @Test
    @DisplayName("기사·다른 발주사·소속 없는 계정은 403(REALTIME-001)이며 연결을 등록하지 않는다")
    void rejectsNonStaff() {
        assertRejected(command(TECHNICIAN_ACCOUNT_ID, ORGANIZATION_ID), RealtimeErrorCode.NOT_OFFICE_MEMBER);
        assertRejected(command(STAFF_ACCOUNT_ID, 2L), RealtimeErrorCode.NOT_OFFICE_MEMBER);
        assertRejected(command(99L, ORGANIZATION_ID), RealtimeErrorCode.NOT_OFFICE_MEMBER);

        assertThat(connections.registeredAccounts()).isEmpty();
    }

    @Test
    @DisplayName("인스턴스가 연결을 더 받을 수 없으면 503(REALTIME-002)이다")
    void rejectsWhenConnectionsAreUnavailable() {
        connections.full();

        assertRejected(command(STAFF_ACCOUNT_ID, ORGANIZATION_ID), RealtimeErrorCode.CONNECTION_UNAVAILABLE);
    }

    private void assertRejected(OpenRealtimeStreamCommand command, RealtimeErrorCode errorCode) {
        assertThatThrownBy(() -> service.open(command))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(errorCode));
    }

    private static OpenRealtimeStreamCommand command(Long accountId, Long organizationId) {
        return new OpenRealtimeStreamCommand(
                accountId, organizationId, ClientType.DESKTOP_WEB, new RecordingRealtimeChannel());
    }
}
