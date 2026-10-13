package com.orbit.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("기사 계약 도메인")
class TechnicianTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(10L);
    private static final AccountId ACCOUNT_ID = new AccountId(7L);
    private static final Instant JOINED_AT = Instant.parse("2026-10-07T00:00:00Z");

    @Test
    @DisplayName("참여 요청 승인으로 맺은 기사 계약은 활성이며 상태 변경 시각은 승인 시각이다")
    void joinedTechnicianIsActive() {
        Technician technician = Technician.join(ORGANIZATION_ID, ACCOUNT_ID, JOINED_AT);

        assertThat(technician.id()).isEmpty();
        assertThat(technician.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(technician.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(technician.status()).isEqualTo(TechnicianStatus.ACTIVE);
        assertThat(technician.statusChangedAt()).isEqualTo(JOINED_AT);
    }

    @Test
    @DisplayName("저장된 기사 계약을 복원한다")
    void restoresTechnician() {
        Technician technician = Technician.reconstitute(
                new TechnicianId(5L), ORGANIZATION_ID, ACCOUNT_ID, TechnicianStatus.DEACTIVATED, JOINED_AT);

        assertThat(technician.id()).contains(new TechnicianId(5L));
        assertThat(technician.status()).isEqualTo(TechnicianStatus.DEACTIVATED);
    }

    @Test
    @DisplayName("복원할 때 식별자가 필요하다")
    void requiresIdToRestore() {
        assertThatThrownBy(() ->
                        Technician.reconstitute(null, ORGANIZATION_ID, ACCOUNT_ID, TechnicianStatus.ACTIVE, JOINED_AT))
                .isInstanceOf(NullPointerException.class);
    }
}
