package com.orbit.schedule.adapter.out.organization;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.orbit.schedule.application.port.out.LoadTechnicianProfilesPort;
import com.orbit.schedule.application.port.out.TechnicianProfile;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;

/**
 * 임시 LoadTechnicianProfilesPort 구현. organization의 기사 계약·기사유형 조회 계약(HM-288·HM-291)과 profile의 계정 이름 조회 계약(HM-305)이 아직
 * 없어 활성 기사도 표시 정보도 없는 것으로 본다(빈 결과). 계약이 생기면 둘을 합쳐 호출하는 어댑터로 교체한 뒤 삭제한다. {@code local}·{@code test}
 * 프로필에서만 등록한다.
 */
@Component
@Profile({"local & !prod", "test & !prod"})
class EmptyTechnicianProfilesAdapter implements LoadTechnicianProfilesPort {

    @Override
    public List<TechnicianProfile> listActive(OrganizationId organizationId) {
        return List.of();
    }

    @Override
    public Map<TechnicianId, TechnicianProfile> findProfiles(
            OrganizationId organizationId, Set<TechnicianId> technicianIds) {
        return Map.of();
    }
}
