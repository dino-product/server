package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.StaffMember;
import com.orbit.organization.TechnicianMember;
import com.orbit.organization.application.port.out.ActiveMember;
import com.orbit.organization.application.port.out.ActiveMemberQueryPort;
import com.orbit.organization.application.port.out.ActiveMembership;
import com.orbit.organization.application.port.out.ActiveTechnicianContract;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.TechnicianId;

@ExtendWith(MockitoExtension.class)
@DisplayName("발주사 활성 구성원 조회")
class OrganizationMemberLookupServiceTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);
    private static final AccountId ACCOUNT_ID = new AccountId(7L);

    @Mock
    private ActiveMemberQueryPort activeMemberQueryPort;

    private OrganizationMemberLookupService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationMemberLookupService(activeMemberQueryPort);
    }

    @Test
    @DisplayName("활성 총관리자 소속이면 총관리자 표시가 있는 직원 구성원이다")
    void returnsOwnerStaffMemberForActiveOwnerMembership() {
        givenActiveMembers(new ActiveMembership(new MembershipId(11L), true));

        assertThat(service.findActiveMember(7L, 100L)).contains(new StaffMember(11L, 100L, true));
    }

    @Test
    @DisplayName("활성 직원 소속이면 총관리자 표시가 없는 직원 구성원이다")
    void returnsStaffMemberForActiveStaffMembership() {
        givenActiveMembers(new ActiveMembership(new MembershipId(12L), false));

        assertThat(service.findActiveMember(7L, 100L)).contains(new StaffMember(12L, 100L, false));
    }

    @Test
    @DisplayName("활성 기사 계약이면 기사 구성원이다")
    void returnsTechnicianMemberForActiveTechnicianContract() {
        givenActiveMembers(new ActiveTechnicianContract(new TechnicianId(21L)));

        assertThat(service.findActiveMember(7L, 100L)).contains(new TechnicianMember(21L, 100L));
    }

    @Test
    @DisplayName("활성 소속도 활성 기사 계약도 없으면 비어 있다")
    void isEmptyWithoutActiveMembershipOrTechnicianContract() {
        givenActiveMembers();

        assertThat(service.findActiveMember(7L, 100L)).isEmpty();
    }

    @Test
    @DisplayName("활성 직원 소속과 활성 기사 계약이 함께 있으면 어느 쪽도 고르지 않고 실패한다")
    void failsWhenBothActiveMembershipAndTechnicianContractExist() {
        givenActiveMembers(
                new ActiveMembership(new MembershipId(11L), false),
                new ActiveTechnicianContract(new TechnicianId(21L)));

        assertThatThrownBy(() -> service.findActiveMember(7L, 100L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("계정·발주사 식별자가 양수가 아니면 호출자 오류다")
    void rejectsNonPositiveIdentifiers() {
        assertThatThrownBy(() -> service.findActiveMember(0L, 100L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findActiveMember(7L, null)).isInstanceOf(IllegalArgumentException.class);
    }

    private void givenActiveMembers(ActiveMember... members) {
        when(activeMemberQueryPort.findActiveMembers(ORGANIZATION_ID, ACCOUNT_ID))
                .thenReturn(List.of(members));
    }
}
