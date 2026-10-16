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

import com.orbit.organization.TechnicianContract;
import com.orbit.organization.application.port.out.AccountTechnicianContract;
import com.orbit.organization.application.port.out.TechnicianContractQueryPort;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.TechnicianId;

@ExtendWith(MockitoExtension.class)
@DisplayName("기사 계약 공개 조회")
class TechnicianContractLookupServiceTest {

    private static final AccountId ACCOUNT_ID = new AccountId(7L);

    @Mock
    private TechnicianContractQueryPort queryPort;

    private TechnicianContractLookupService service;

    @BeforeEach
    void setUp() {
        service = new TechnicianContractLookupService(queryPort);
    }

    @Test
    @DisplayName("여러 발주사의 활성·비활성 기사 계약을 활성 여부와 함께 돌려준다")
    void returnsActiveAndInactiveContractsOfAllOrganizations() {
        when(queryPort.listByAccount(ACCOUNT_ID))
                .thenReturn(List.of(
                        new AccountTechnicianContract(new TechnicianId(21L), new OrganizationId(100L), true),
                        new AccountTechnicianContract(new TechnicianId(22L), new OrganizationId(200L), false)));

        assertThat(service.listByAccount(7L))
                .containsExactly(new TechnicianContract(21L, 100L, true), new TechnicianContract(22L, 200L, false));
    }

    @Test
    @DisplayName("기사 계약이 없으면 빈 목록이다")
    void isEmptyWithoutContracts() {
        when(queryPort.listByAccount(ACCOUNT_ID)).thenReturn(List.of());

        assertThat(service.listByAccount(7L)).isEmpty();
    }

    @Test
    @DisplayName("계정 식별자가 양수가 아니면 호출자 오류다")
    void rejectsNonPositiveAccountId() {
        assertThatThrownBy(() -> service.listByAccount(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listByAccount(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
