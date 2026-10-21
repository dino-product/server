package com.orbit.schedule.adapter.out.organization;

import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.orbit.schedule.application.port.out.LoadWorkTypeLabelsPort;
import com.orbit.schedule.application.port.out.TypeLabel;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * 임시 LoadWorkTypeLabelsPort 구현. organization에 작업유형이 아직 없어(HM-295) 표시 정보가 없는 것으로 본다(빈 결과). 작업유형 조회
 * 계약이 생기면 이를 호출하는 어댑터로 교체한 뒤 삭제한다. {@code local}·{@code test}·{@code dev} 프로필에서만 등록하며
 * 운영({@code prod})에는 등록하지 않는다.
 */
@Component
@Profile({"local & !prod", "test & !prod", "dev & !prod"})
class EmptyWorkTypeLabelsAdapter implements LoadWorkTypeLabelsPort {

    @Override
    public Map<WorkTypeId, TypeLabel> findLabels(OrganizationId organizationId, Set<WorkTypeId> workTypeIds) {
        return Map.of();
    }
}
