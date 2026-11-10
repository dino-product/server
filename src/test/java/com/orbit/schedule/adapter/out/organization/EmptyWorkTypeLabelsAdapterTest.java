package com.orbit.schedule.adapter.out.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkTypeId;

@DisplayName("임시 작업유형 표시 정보 어댑터")
class EmptyWorkTypeLabelsAdapterTest {

    @Test
    @DisplayName("작업유형의 표시 정보가 없는 것으로 본다")
    void findsNothing() {
        assertThat(new EmptyWorkTypeLabelsAdapter().findLabels(new OrganizationId(100L), Set.of(new WorkTypeId(2L))))
                .isEmpty();
    }
}
