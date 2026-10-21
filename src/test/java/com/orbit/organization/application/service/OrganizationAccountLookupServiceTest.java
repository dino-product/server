package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.application.port.out.MemberAccountQueryPort;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.TechnicianId;

/** 활성·비활성·다른 발주사·없는 행의 판정은 저장소가 하므로 {@code MemberAccountQueryAdapterTest}가 검증한다. */
@ExtendWith(MockitoExtension.class)
@DisplayName("발주사 구성원 계정 공개 조회")
class OrganizationAccountLookupServiceTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);

    @Mock
    private MemberAccountQueryPort queryPort;

    private OrganizationAccountLookupService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationAccountLookupService(queryPort);
    }

    @Test
    @DisplayName("이 발주사의 활성 직원 소속이면 그 계정 식별자를 돌려준다")
    void returnsAccountOfActiveStaffMembership() {
        when(queryPort.findActiveStaffAccountId(ORGANIZATION_ID, new MembershipId(11L)))
                .thenReturn(Optional.of(new AccountId(7L)));

        assertThat(service.findActiveStaffAccountId(100L, 11L)).contains(7L);
    }

    @Test
    @DisplayName("활성 직원 소속을 찾지 못하면(다른 발주사·비활성·없음) 빈 값이다")
    void isEmptyWithoutActiveStaffMembership() {
        when(queryPort.findActiveStaffAccountId(ORGANIZATION_ID, new MembershipId(12L)))
                .thenReturn(Optional.empty());

        assertThat(service.findActiveStaffAccountId(100L, 12L)).isEmpty();
    }

    @Test
    @DisplayName("직원 소속 조회의 발주사·소속 식별자가 null이거나 양수가 아니면 호출자 오류다")
    void rejectsInvalidIdentifiersForStaff() {
        assertThatThrownBy(() -> service.findActiveStaffAccountId(null, 11L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findActiveStaffAccountId(0L, 11L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findActiveStaffAccountId(100L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findActiveStaffAccountId(100L, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이 발주사의 활성 기사 계약이면 그 계정 식별자를 돌려준다")
    void returnsAccountOfActiveTechnicianContract() {
        when(queryPort.findActiveTechnicianAccountId(ORGANIZATION_ID, new TechnicianId(21L)))
                .thenReturn(Optional.of(new AccountId(8L)));

        assertThat(service.findActiveTechnicianAccountId(100L, 21L)).contains(8L);
    }

    @Test
    @DisplayName("활성 기사 계약을 찾지 못하면(다른 발주사·비활성·없음) 빈 값이다")
    void isEmptyWithoutActiveTechnicianContract() {
        when(queryPort.findActiveTechnicianAccountId(ORGANIZATION_ID, new TechnicianId(22L)))
                .thenReturn(Optional.empty());

        assertThat(service.findActiveTechnicianAccountId(100L, 22L)).isEmpty();
    }

    @Test
    @DisplayName("기사 계약 조회의 발주사·계약 식별자가 null이거나 양수가 아니면 호출자 오류다")
    void rejectsInvalidIdentifiersForTechnician() {
        assertThatThrownBy(() -> service.findActiveTechnicianAccountId(null, 21L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findActiveTechnicianAccountId(-1L, 21L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findActiveTechnicianAccountId(100L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findActiveTechnicianAccountId(100L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
