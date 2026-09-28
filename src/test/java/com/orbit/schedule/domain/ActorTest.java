package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("작업 요청자")
class ActorTest {

    private static final MembershipId MEMBERSHIP_ID = new MembershipId(1L);
    private static final TechnicianId TECHNICIAN_ID = new TechnicianId(3L);
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);

    @Test
    @DisplayName("관리자는 소속·조직·역할이 모두 있어야 한다")
    void managerRequiresEveryValue() {
        assertThatThrownBy(() -> new ManagerActor(null, ORGANIZATION_ID, ActorRole.STAFF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("membershipId must not be null");
        assertThatThrownBy(() -> new ManagerActor(MEMBERSHIP_ID, null, ActorRole.STAFF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("organizationId must not be null");
        assertThatThrownBy(() -> new ManagerActor(MEMBERSHIP_ID, ORGANIZATION_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("role must not be null");
    }

    @Test
    @DisplayName("기사는 기사 계약과 조직이 모두 있어야 한다")
    void technicianRequiresEveryValue() {
        assertThatThrownBy(() -> new TechnicianActor(null, ORGANIZATION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("technicianId must not be null");
        assertThatThrownBy(() -> new TechnicianActor(TECHNICIAN_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("organizationId must not be null");
    }
}
