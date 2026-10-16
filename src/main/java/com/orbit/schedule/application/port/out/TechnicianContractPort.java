package com.orbit.schedule.application.port.out;

import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;

/**
 * 배정할 기사가 그 조직의 활성 기사 계약인지 확인하는 출력 포트. 구현은 organization 모듈의 공개 계약으로 기사 계약의 소속 조직과 상태를 확인한다. 다른 조직의
 * 기사 계약이거나 비활성·역할 변경으로 끝난 계약, 없는 계약이면 {@code false}다. 인자는 null이 아니다. 서비스는 그 기사를 잠근 뒤 호출하므로, 구현은 호출
 * 시점에 커밋된 상태를 새로 읽어야 하며 캐시나 영속성 컨텍스트에 미리 올린 엔티티를 쓰지 않는다.
 */
public interface TechnicianContractPort {

    boolean isActive(OrganizationId organizationId, TechnicianId technicianId);
}
