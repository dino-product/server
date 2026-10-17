package com.orbit.schedule.adapter.out.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.WorkTypeId;

@DisplayName("임시 표시 정보 어댑터")
class EmptyDisplayInfoAdaptersTest {

    private static final OrganizationId ORGANIZATION = new OrganizationId(100L);

    @Test
    @DisplayName("활성 기사도, 기사·등록자·작업유형의 표시 정보도 없는 것으로 본다")
    void findsNothing() {
        EmptyTechnicianProfilesAdapter technicians = new EmptyTechnicianProfilesAdapter();

        assertThat(technicians.listActive(ORGANIZATION)).isEmpty();
        assertThat(technicians.findProfiles(ORGANIZATION, Set.of(new TechnicianId(3L))))
                .isEmpty();
        assertThat(new EmptyStaffProfilesAdapter().findProfiles(ORGANIZATION, Set.of(new MembershipId(1L))))
                .isEmpty();
        assertThat(new EmptyWorkTypeLabelsAdapter().findLabels(ORGANIZATION, Set.of(new WorkTypeId(2L))))
                .isEmpty();
    }
}
