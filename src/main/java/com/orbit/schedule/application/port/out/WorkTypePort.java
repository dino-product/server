package com.orbit.schedule.application.port.out;

import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * 작업에 지정할 작업 유형이 그 조직에서 선택할 수 있는 유형인지 확인하는 출력 포트. 구현은 organization 모듈의 공개 계약으로 유형의 소속 조직과 삭제 여부를
 * 확인한다. 다른 조직의 유형이거나 삭제된 유형, 없는 유형이면 {@code false}다. 인자는 null이 아니다.
 */
public interface WorkTypePort {

    boolean isSelectable(OrganizationId organizationId, WorkTypeId workTypeId);
}
