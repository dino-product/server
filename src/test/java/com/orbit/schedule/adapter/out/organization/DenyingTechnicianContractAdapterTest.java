package com.orbit.schedule.adapter.out.organization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;

@DisplayName("임시 기사 계약 확인 어댑터")
class DenyingTechnicianContractAdapterTest {

    @Test
    @DisplayName("organization 계약이 생기기 전에는 어느 기사도 활성 기사 계약으로 보지 않는다")
    void deniesEveryTechnician() {
        assertThat(new DenyingTechnicianContractAdapter().isActive(new OrganizationId(100L), new TechnicianId(3L)))
                .isFalse();
    }
}
