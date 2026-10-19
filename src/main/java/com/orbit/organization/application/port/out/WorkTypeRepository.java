package com.orbit.organization.application.port.out;

import java.util.List;
import java.util.Optional;

import com.orbit.organization.domain.CategoryName;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.WorkType;
import com.orbit.organization.domain.WorkTypeId;

/** 작업 유형 저장소. 이름 유일 인덱스 위반은 {@link DuplicateCategoryNameException}으로 알린다. */
public interface WorkTypeRepository {

    WorkType save(WorkType workType);

    /** 삭제된 작업 유형도 찾는다. 다른 발주사 유형인지는 호출자가 확인한다. */
    Optional<WorkType> findById(WorkTypeId id);

    /** 같은 발주사에서 이름이 정확히 같은 삭제되지 않은 작업 유형. */
    Optional<WorkType> findUndeletedByName(OrganizationId organizationId, CategoryName name);

    /** 같은 발주사의 삭제되지 않은 작업 유형을 만든 순서대로 돌려준다([조직·계정] §7). */
    List<WorkType> listUndeleted(OrganizationId organizationId);
}
