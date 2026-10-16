package com.orbit.schedule.application.port.out;

import java.util.Map;
import java.util.Set;

import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * 작업유형의 표시 정보(이름·색)를 찾는 출력 포트(ACL). 선택할 수 있는지 확인하는 {@link WorkTypePort}와 달리 이미 작업에 붙은 유형을 보여 주는 데 쓴다. 구현은
 * organization 모듈의 작업유형 공개 계약으로 조회한다. 인자는 null이 아니다.
 */
public interface LoadWorkTypeLabelsPort {

    /** 지정한 작업유형의 표시 정보를 유형 식별자별로 돌려준다. 삭제된 유형도 담고, 다른 조직의 유형이거나 찾을 수 없으면 결과에서 빠진다. */
    Map<WorkTypeId, TypeLabel> findLabels(OrganizationId organizationId, Set<WorkTypeId> workTypeIds);
}
