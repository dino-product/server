package com.orbit.schedule.adapter.out.organization;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.orbit.schedule.application.port.out.WorkTypePort;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * 임시 WorkTypePort 구현. organization에 작업 유형이 아직 없어(HM-295) 선택할 수 있는 유형도 없으므로 모든 유형을 거부한다. 작업 유형을
 * 비운 등록·수정과 지금 유형을 그대로 보낸 수정(확인하지 않음)만 통과한다. organization의 작업 유형 확인 공개 계약이 생기면 이를 호출하는
 * 어댑터로 교체한 뒤 삭제한다(HM-295). {@code local}·{@code test} 프로필에서만 등록하며 그 밖의 프로필은 Bean 부재로 기동하지 않는다.
 */
@Component
@Profile({"local & !prod", "test & !prod"})
class DenyingWorkTypeAdapter implements WorkTypePort {

    @Override
    public boolean isSelectable(OrganizationId organizationId, WorkTypeId workTypeId) {
        return false;
    }
}
