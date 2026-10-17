package com.orbit.schedule.adapter.out.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.TechnicianContractLookup;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;

@ExtendWith(MockitoExtension.class)
@DisplayName("organization 기사 계약 활성 확인을 배정 대상 확인으로 번역")
class OrganizationTechnicianContractAdapterTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);
    private static final TechnicianId TECHNICIAN_ID = new TechnicianId(21L);

    @Mock
    private TechnicianContractLookup contractLookup;

    private OrganizationTechnicianContractAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OrganizationTechnicianContractAdapter(contractLookup);
    }

    @Test
    @DisplayName("organization이 그 발주사의 활성 기사 계약으로 보면 배정할 수 있다")
    void isActiveWhenOrganizationConfirmsActiveContract() {
        when(contractLookup.existsActive(100L, 21L)).thenReturn(true);

        assertThat(adapter.isActive(ORGANIZATION_ID, TECHNICIAN_ID)).isTrue();
    }

    @Test
    @DisplayName("organization이 활성 기사 계약이 아니라고 하면 배정할 수 없다")
    void isNotActiveWhenOrganizationDeniesContract() {
        when(contractLookup.existsActive(100L, 21L)).thenReturn(false);

        assertThat(adapter.isActive(ORGANIZATION_ID, TECHNICIAN_ID)).isFalse();
    }
}
