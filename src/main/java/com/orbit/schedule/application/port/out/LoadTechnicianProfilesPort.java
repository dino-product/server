package com.orbit.schedule.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;

/**
 * 조직 기사 계약의 표시 정보(이름·기사유형)를 찾는 출력 포트(ACL). 구현은 기사 계약·기사유형을 organization 모듈의 공개 계약으로, 계정 이름을 profile 모듈의
 * 이름 조회 계약({@code ProfileLookup}, HM-305)으로 찾아 합친다. 인자는 null이 아니다.
 */
public interface LoadTechnicianProfilesPort {

    /** 그 조직의 활성 기사 계약 전부. 타임테이블의 기사 행에 쓴다. 순서는 보장하지 않는다. */
    List<TechnicianProfile> listActive(OrganizationId organizationId);

    /**
     * 지정한 기사 계약의 표시 정보를 기사 계약 식별자별로 돌려준다. 활성 여부와 관계없이(비활성·해지된 기사 계약 포함) 그 조직의 기사 계약이면 담고, 다른
     * 조직의 기사 계약이거나 찾을 수 없으면 결과에서 빠진다.
     */
    Map<TechnicianId, TechnicianProfile> findProfiles(OrganizationId organizationId, Set<TechnicianId> technicianIds);
}
