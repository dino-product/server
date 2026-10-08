package com.orbit.organization.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("기사 계약 도메인")
class TechnicianTest {

    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");

    @Test
    @DisplayName("상태 변경 시각은 계약 시각보다 앞설 수 없다")
    void rejectsStatusChangeBeforeContract() {
        assertThatThrownBy(() -> Technician.reconstitute(
                        new TechnicianId(1L),
                        new OrganizationId(100L),
                        new AccountId(7L),
                        TechnicianStatus.ACTIVE,
                        NOW,
                        NOW.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
