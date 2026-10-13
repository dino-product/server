package com.orbit.organization.application.port.out;

import java.time.Instant;

import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.Technician;

/** 기사 계약 저장소. */
public interface TechnicianRepository {

    /** 새 기사 계약을 만든다. */
    Technician save(Technician technician);

    /** 그 발주사에서 이 기사 유형이 지정된 모든 기사 계약(상태 무관)을 "유형 없음"으로 바꾸고 바뀐 계약 수를 돌려준다. */
    int unassignTechnicianCategory(OrganizationId organizationId, MemberCategoryId categoryId, Instant unassignedAt);
}
