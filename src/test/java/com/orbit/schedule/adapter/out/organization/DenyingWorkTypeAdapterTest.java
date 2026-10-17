package com.orbit.schedule.adapter.out.organization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkTypeId;

@DisplayName("임시 작업 유형 확인 어댑터")
class DenyingWorkTypeAdapterTest {

    @Test
    @DisplayName("작업 유형 기능이 생기기 전에는 어느 유형도 선택할 수 없다")
    void deniesEveryWorkType() {
        assertThat(new DenyingWorkTypeAdapter().isSelectable(new OrganizationId(100L), new WorkTypeId(2L)))
                .isFalse();
    }
}
