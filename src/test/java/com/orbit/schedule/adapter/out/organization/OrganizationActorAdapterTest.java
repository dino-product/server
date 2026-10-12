package com.orbit.schedule.adapter.out.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.OrganizationMemberLookup;
import com.orbit.organization.StaffMember;
import com.orbit.organization.TechnicianMember;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianActor;
import com.orbit.schedule.domain.TechnicianId;

@ExtendWith(MockitoExtension.class)
@DisplayName("organization 구성원을 요청자로 번역")
class OrganizationActorAdapterTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);

    @Mock
    private OrganizationMemberLookup memberLookup;

    private OrganizationActorAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OrganizationActorAdapter(memberLookup);
    }

    @Test
    @DisplayName("총관리자 표시가 있는 직원 소속은 총관리자 관리자다")
    void translatesOwnerStaffMemberToOwnerManager() {
        when(memberLookup.findActiveMember(7L, 100L)).thenReturn(Optional.of(new StaffMember(11L, 100L, true)));

        assertThat(adapter.findActiveActor(7L, ORGANIZATION_ID))
                .contains(new ManagerActor(new MembershipId(11L), ORGANIZATION_ID, ActorRole.OWNER));
    }

    @Test
    @DisplayName("총관리자 표시가 없는 직원 소속은 직원 관리자다")
    void translatesStaffMemberToStaffManager() {
        when(memberLookup.findActiveMember(7L, 100L)).thenReturn(Optional.of(new StaffMember(12L, 100L, false)));

        assertThat(adapter.findActiveActor(7L, ORGANIZATION_ID))
                .contains(new ManagerActor(new MembershipId(12L), ORGANIZATION_ID, ActorRole.STAFF));
    }

    @Test
    @DisplayName("기사 계약은 같은 ID의 기사다")
    void translatesTechnicianMemberToTechnician() {
        when(memberLookup.findActiveMember(7L, 100L)).thenReturn(Optional.of(new TechnicianMember(21L, 100L)));

        assertThat(adapter.findActiveActor(7L, ORGANIZATION_ID))
                .contains(new TechnicianActor(new TechnicianId(21L), ORGANIZATION_ID));
    }

    @Test
    @DisplayName("활성 구성원이 아니면 요청자가 없다")
    void isEmptyWithoutActiveMember() {
        when(memberLookup.findActiveMember(7L, 100L)).thenReturn(Optional.empty());

        assertThat(adapter.findActiveActor(7L, ORGANIZATION_ID)).isEmpty();
    }
}
