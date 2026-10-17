package com.orbit.organization.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.TechnicianContract;
import com.orbit.organization.TechnicianContractLookup;
import com.orbit.organization.application.port.out.AccountTechnicianContract;
import com.orbit.organization.application.port.out.TechnicianContractQueryPort;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.TechnicianId;

@Service
public class TechnicianContractLookupService implements TechnicianContractLookup {

    private final TechnicianContractQueryPort queryPort;

    public TechnicianContractLookupService(TechnicianContractQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianContract> listByAccount(Long accountId) {
        return queryPort.listByAccount(new AccountId(accountId)).stream()
                .map(TechnicianContractLookupService::toTechnicianContract)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActive(Long organizationId, Long technicianId) {
        return queryPort.existsActive(new OrganizationId(organizationId), new TechnicianId(technicianId));
    }

    private static TechnicianContract toTechnicianContract(AccountTechnicianContract contract) {
        return new TechnicianContract(
                contract.technicianId().value(), contract.organizationId().value(), contract.active());
    }
}
