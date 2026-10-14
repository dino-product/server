package com.orbit.schedule.adapter.out.organization;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.orbit.schedule.application.port.out.TechnicianContractPort;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;

/**
 * 임시 TechnicianContractPort 구현. organization이 기사 계약의 소속 조직·상태를 확인하는 공개 계약을 아직 제공하지 않아, 확인하지 못한 기사에게 배정하는 가짜
 * 성공을 만들지 않도록 모든 기사를 거부한다. 그 계약이 생기면 이를 호출하는 어댑터로 교체한 뒤 삭제한다(HM-282). {@code local}·{@code test} 프로필에서만
 * 등록하며 그 밖의 프로필은 Bean 부재로 기동하지 않는다.
 */
@Component
@Profile({"local & !prod", "test & !prod"})
class DenyingTechnicianContractAdapter implements TechnicianContractPort {

    @Override
    public boolean isActive(OrganizationId organizationId, TechnicianId technicianId) {
        return false;
    }
}
