package com.orbit.schedule.adapter.out.organization;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.orbit.schedule.application.port.out.LoadTechnicianNamePort;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;

/**
 * 임시 LoadTechnicianNamePort 구현. 기사 계약 조회 공개 계약이 없어 모든 기사에게 식별자를 붙인 가짜 이름({@code 기사 식별자})을 돌려준다. organization의 공개
 * 계약이 생기면 그것을 호출하는 어댑터로 교체한 뒤 삭제한다. {@code local}·{@code test} 프로필에서만 등록한다.
 */
@Component
@Profile({"local & !prod", "test & !prod"})
class FakeTechnicianNameAdapter implements LoadTechnicianNamePort {

    @Override
    public Map<TechnicianId, String> loadNames(OrganizationId organizationId, Set<TechnicianId> technicianIds) {
        return technicianIds.stream()
                .collect(Collectors.toMap(Function.identity(), technicianId -> "기사 " + technicianId.value()));
    }
}
