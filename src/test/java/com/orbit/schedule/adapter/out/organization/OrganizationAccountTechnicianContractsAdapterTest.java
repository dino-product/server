package com.orbit.schedule.adapter.out.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.TechnicianContract;
import com.orbit.organization.TechnicianContractLookup;
import com.orbit.schedule.application.port.out.AccountTechnicianContract;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;

@ExtendWith(MockitoExtension.class)
@DisplayName("organization 계정별 기사 계약을 schedule 기사 계약으로 번역")
class OrganizationAccountTechnicianContractsAdapterTest {

    @Mock
    private TechnicianContractLookup contractLookup;

    private OrganizationAccountTechnicianContractsAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OrganizationAccountTechnicianContractsAdapter(contractLookup);
    }

    @Test
    @DisplayName("발주사마다의 기사 계약을 비활성 계약까지 활성 여부와 함께 옮긴다")
    void translatesEveryContractWithActiveFlag() {
        when(contractLookup.listByAccount(7L))
                .thenReturn(List.of(new TechnicianContract(21L, 100L, true), new TechnicianContract(22L, 200L, false)));

        assertThat(adapter.listByAccount(7L))
                .containsExactly(
                        new AccountTechnicianContract(new TechnicianId(21L), new OrganizationId(100L), true),
                        new AccountTechnicianContract(new TechnicianId(22L), new OrganizationId(200L), false));
    }

    @Test
    @DisplayName("기사 계약이 없는 계정은 빈 목록이다")
    void isEmptyWithoutContract() {
        when(contractLookup.listByAccount(7L)).thenReturn(List.of());

        assertThat(adapter.listByAccount(7L)).isEmpty();
    }
}
