package com.orbit.schedule.adapter.out.organization;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("임시 계정 기사 계약 조회 어댑터")
class DenyingAccountTechnicianContractsAdapterTest {

    @Test
    @DisplayName("organization 계약이 생기기 전에는 빈 목록으로 가짜 성공을 만들지 않고 조회를 거부한다")
    void rejectsEveryLookup() {
        assertThatThrownBy(() -> new DenyingAccountTechnicianContractsAdapter().listByAccount(7L))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
