package com.orbit.schedule.adapter.out.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;

@DisplayName("임시 기사 이름 어댑터")
class FakeTechnicianNameAdapterTest {

    @Test
    @DisplayName("요청한 기사마다 식별자를 붙인 가짜 이름을 돌려준다")
    void namesEveryTechnician() {
        assertThat(new FakeTechnicianNameAdapter()
                        .loadNames(new OrganizationId(100L), Set.of(new TechnicianId(3L), new TechnicianId(4L))))
                .isEqualTo(Map.of(new TechnicianId(3L), "기사 3", new TechnicianId(4L), "기사 4"));
    }
}
