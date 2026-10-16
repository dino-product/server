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
    private static final Instant DEACTIVATED_AT = Instant.parse("2026-10-08T00:00:00Z");
    private static final Instant REJOINED_AT = Instant.parse("2026-10-09T00:00:00Z");

    @Test
    @DisplayName("참여 요청 승인으로 맺은 기사 계약은 활성이며 참여일·상태 변경 시각은 승인 시각이다")
    void joinedTechnicianIsActive() {
        Technician technician = Technician.join(ORGANIZATION_ID, ACCOUNT_ID, JOINED_AT);

        assertThat(technician.id()).isEmpty();
        assertThat(technician.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(technician.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(technician.status()).isEqualTo(TechnicianStatus.ACTIVE);
        assertThat(technician.joinedAt()).isEqualTo(JOINED_AT);
        assertThat(technician.statusChangedAt()).isEqualTo(JOINED_AT);
    }

    @Test
    @DisplayName("다른 역할로 있던 회원이 기사로 다시 참여하면 이전 참여일을 이어받고 상태 변경 시각은 승인 시각이다")
    void joinedTechnicianKeepsEarlierJoinDate() {
        Technician technician = Technician.join(ORGANIZATION_ID, ACCOUNT_ID, JOINED_AT, REJOINED_AT);

        assertThat(technician.status()).isEqualTo(TechnicianStatus.ACTIVE);
        assertThat(technician.joinedAt()).isEqualTo(JOINED_AT);
        assertThat(technician.statusChangedAt()).isEqualTo(REJOINED_AT);
    }

    @Test
    @DisplayName("비활성 기사 계약을 다시 활성으로 바꾸면 같은 계약 ID와 참여일을 유지한다")
    void rejoinsDeactivatedTechnician() {
        Technician rejoined = deactivated().rejoin(REJOINED_AT);

        assertThat(rejoined.id()).contains(new TechnicianId(5L));
        assertThat(rejoined.status()).isEqualTo(TechnicianStatus.ACTIVE);
        assertThat(rejoined.joinedAt()).isEqualTo(JOINED_AT);
        assertThat(rejoined.statusChangedAt()).isEqualTo(REJOINED_AT);
    }

    @Test
    @DisplayName("활성 기사 계약은 다시 활성으로 바꿀 수 없다")
    void refusesRejoiningActiveTechnician() {
        Technician active = Technician.reconstitute(
                new TechnicianId(5L), ORGANIZATION_ID, ACCOUNT_ID, TechnicianStatus.ACTIVE, JOINED_AT, JOINED_AT);

        assertThatThrownBy(() -> active.rejoin(REJOINED_AT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("저장된 기사 계약을 복원한다")
    void restoresTechnician() {
        Technician technician = deactivated();

        assertThat(technician.id()).contains(new TechnicianId(5L));
        assertThat(technician.status()).isEqualTo(TechnicianStatus.DEACTIVATED);
        assertThat(technician.joinedAt()).isEqualTo(JOINED_AT);
    }

    @Test
    @DisplayName("복원할 때 식별자가 필요하고, 상태 변경 시각은 참여일보다 앞설 수 없다")
    void validatesRestoredTechnician() {
        assertThatThrownBy(() -> Technician.reconstitute(
                        null, ORGANIZATION_ID, ACCOUNT_ID, TechnicianStatus.ACTIVE, JOINED_AT, JOINED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Technician.reconstitute(
                        new TechnicianId(5L),
                        ORGANIZATION_ID,
                        ACCOUNT_ID,
                        TechnicianStatus.ACTIVE,
                        JOINED_AT,
                        JOINED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Technician deactivated() {
        return Technician.reconstitute(
                new TechnicianId(5L),
                ORGANIZATION_ID,
                ACCOUNT_ID,
                TechnicianStatus.DEACTIVATED,
                JOINED_AT,
                DEACTIVATED_AT);
    }
}
