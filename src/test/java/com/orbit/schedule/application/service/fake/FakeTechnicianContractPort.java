package com.orbit.schedule.application.service.fake;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.orbit.schedule.application.port.out.TechnicianContractPort;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;

/**
 * 서비스 단위 테스트용 TechnicianContractPort. 스캔되지 않도록 애너테이션을 붙이지 않는다. 등록한 조직·기사 조합만 활성 기사 계약으로 보고, 확인 요청 횟수를
 * 기록해 확인 전에 끝나야 하는 요청이 포트를 부르지 않았는지 보게 한다.
 */
public class FakeTechnicianContractPort implements TechnicianContractPort {

    private final Set<Contract> activeContracts = new HashSet<>();
    private int checks;

    public void givenActive(OrganizationId organizationId, TechnicianId technicianId) {
        activeContracts.add(new Contract(organizationId, technicianId));
    }

    public void givenInactive(OrganizationId organizationId, TechnicianId technicianId) {
        activeContracts.remove(new Contract(organizationId, technicianId));
    }

    public int checks() {
        return checks;
    }

    @Override
    public boolean isActive(OrganizationId organizationId, TechnicianId technicianId) {
        // 실제 포트도 식별자를 요구하므로, 서비스가 형식 확인 전에 부르면 여기서 다른 메시지로 드러난다.
        Objects.requireNonNull(organizationId, "fake port received null organizationId");
        Objects.requireNonNull(technicianId, "fake port received null technicianId");
        checks++;
        return activeContracts.contains(new Contract(organizationId, technicianId));
    }

    private record Contract(OrganizationId organizationId, TechnicianId technicianId) {}
}
