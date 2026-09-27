package com.orbit.schedule.application.port.out;

import java.util.Map;
import java.util.Set;

import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;

/**
 * 조직 기사 계약의 표시 이름을 찾는 출력 포트. 구현은 organization 모듈의 공개 계약으로 기사 계약의 이름을 조회한다. 그 조직의 기사 계약이 아니거나 이름을 찾을 수
 * 없는 기사는 결과에서 빠진다(비활성 기사 계약도 지난 일정에는 이름을 보여 준다). 인자는 null이 아니다.
 */
public interface LoadTechnicianNamePort {

    Map<TechnicianId, String> loadNames(OrganizationId organizationId, Set<TechnicianId> technicianIds);
}
