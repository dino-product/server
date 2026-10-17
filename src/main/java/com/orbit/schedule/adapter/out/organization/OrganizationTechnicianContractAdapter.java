package com.orbit.schedule.adapter.out.organization;

import org.springframework.stereotype.Component;

import com.orbit.organization.TechnicianContractLookup;
import com.orbit.schedule.application.port.out.TechnicianContractPort;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;

/**
 * organization의 기사 계약 활성 확인을 schedule의 배정 대상 확인으로 옮기는 ACL 어댑터. 다른 발주사·비활성·없는 계약을 구분하지 않는 판정과 호출 시점에
 * 커밋된 상태를 새로 읽는 보장은 organization 계약이 맡는다.
 */
@Component
class OrganizationTechnicianContractAdapter implements TechnicianContractPort {

    private final TechnicianContractLookup contractLookup;

    OrganizationTechnicianContractAdapter(TechnicianContractLookup contractLookup) {
        this.contractLookup = contractLookup;
    }

    @Override
    public boolean isActive(OrganizationId organizationId, TechnicianId technicianId) {
        return contractLookup.existsActive(organizationId.value(), technicianId.value());
    }
}
