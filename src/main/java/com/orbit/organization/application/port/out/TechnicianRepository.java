package com.orbit.organization.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.Technician;
import com.orbit.organization.domain.TechnicianId;

/** 기사 계약 저장소. */
public interface TechnicianRepository {

    /** 새 기사 계약을 만든다. */
    Technician save(Technician technician);

    Optional<Technician> findById(TechnicianId id);

    /** 계정의 그 발주사 기사 계약(상태 무관). 계정·발주사당 1행이다. */
    Optional<Technician> find(OrganizationId organizationId, AccountId accountId);

    /**
     * {@link Technician#reactivate}으로 다시 활성이 된 계약의 상태·상태 변경 시각을 저장한다. {@code unassignCategory}이면 기사 유형도 "유형 없음"으로 바꾼다.
     * 기사 유형은 계약 도메인에 없으므로, 유형 삭제가 해제한 값을 되살리지 않도록 호출자는 발주사를 잠근 뒤 읽은 계약을 넘긴다.
     */
    Technician reactivate(Technician technician, boolean unassignCategory);

    /** {@link Technician#deactivate}로 비활성이 된 계약의 상태·상태 변경 시각을 저장한다. 기사 유형은 그대로 둔다. */
    void deactivate(Technician technician);

    /** 기사 유형을 바꾼다. {@code categoryId}가 {@code null}이면 "유형 없음"이다. 유형은 계약 도메인에 없으므로 호출자는 발주사를 잠근 뒤 유형을 확인한다. */
    void changeTechnicianCategory(TechnicianId id, MemberCategoryId categoryId);

    /** 그 발주사에서 이 기사 유형이 지정된 모든 기사 계약(상태 무관)을 "유형 없음"으로 바꾸고 바뀐 계약 수를 돌려준다. */
    int unassignTechnicianCategory(OrganizationId organizationId, MemberCategoryId categoryId, Instant unassignedAt);
}
