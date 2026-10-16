package com.orbit.schedule.application.service.fake;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.orbit.schedule.application.port.out.AccountTechnicianContract;
import com.orbit.schedule.application.port.out.AccountTechnicianContractsPort;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;

/** 서비스 단위 테스트용 AccountTechnicianContractsPort. 스캔되지 않도록 애너테이션을 붙이지 않는다. 지정한 계정의 계약을 지정한 순서대로 돌려준다. */
public class FakeAccountTechnicianContractsPort implements AccountTechnicianContractsPort {

    private final Map<Long, List<AccountTechnicianContract>> contracts = new LinkedHashMap<>();

    @Override
    public List<AccountTechnicianContract> listByAccount(Long accountId) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        return List.copyOf(contracts.getOrDefault(accountId, List.of()));
    }

    public void givenActive(Long accountId, OrganizationId organizationId, TechnicianId technicianId) {
        given(accountId, new AccountTechnicianContract(technicianId, organizationId, true));
    }

    public void givenInactive(Long accountId, OrganizationId organizationId, TechnicianId technicianId) {
        given(accountId, new AccountTechnicianContract(technicianId, organizationId, false));
    }

    private void given(Long accountId, AccountTechnicianContract contract) {
        contracts.computeIfAbsent(accountId, ignored -> new ArrayList<>()).add(contract);
    }
}
