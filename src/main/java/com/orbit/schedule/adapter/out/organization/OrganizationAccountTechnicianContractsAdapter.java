package com.orbit.schedule.adapter.out.organization;

import java.util.List;

import org.springframework.stereotype.Component;

import com.orbit.organization.TechnicianContract;
import com.orbit.organization.TechnicianContractLookup;
import com.orbit.schedule.application.port.out.AccountTechnicianContract;
import com.orbit.schedule.application.port.out.AccountTechnicianContractsPort;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;

/**
 * organization의 계정별 기사 계약 목록을 schedule의 기사 계약으로 옮기는 ACL 어댑터. 비활성 계약을 활성 여부와 함께 담는 것과 호출 시점에 커밋된 상태를 새로
 * 읽는 보장은 organization 계약이 맡는다. 기사 계약 확인({@link OrganizationTechnicianContractAdapter})과 같은 계약을 쓰지만 포트마다 클래스를 나눠, 한
 * 포트만 대체하는 테스트가 다른 포트의 구현까지 지우지 않게 한다.
 */
@Component
class OrganizationAccountTechnicianContractsAdapter implements AccountTechnicianContractsPort {

    private final TechnicianContractLookup contractLookup;

    OrganizationAccountTechnicianContractsAdapter(TechnicianContractLookup contractLookup) {
        this.contractLookup = contractLookup;
    }

    @Override
    public List<AccountTechnicianContract> listByAccount(Long accountId) {
        return contractLookup.listByAccount(accountId).stream()
                .map(OrganizationAccountTechnicianContractsAdapter::toContract)
                .toList();
    }

    private static AccountTechnicianContract toContract(TechnicianContract contract) {
        return new AccountTechnicianContract(
                new TechnicianId(contract.technicianId()),
                new OrganizationId(contract.organizationId()),
                contract.active());
    }
}
