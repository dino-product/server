package com.orbit.schedule.adapter.out.organization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.domain.OrganizationId;

@DisplayName("임시 행위자 어댑터")
class DenyingActorAdapterTest {

    @Test
    @DisplayName("organization 계약이 연결되기 전에는 어떤 계정도 행위자로 인정하지 않는다")
    void deniesEveryAccount() {
        assertThat(new DenyingActorAdapter().findActiveActor(1L, new OrganizationId(100L)))
                .isEmpty();
    }
}
