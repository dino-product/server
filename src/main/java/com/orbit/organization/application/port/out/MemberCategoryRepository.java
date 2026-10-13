package com.orbit.organization.application.port.out;

import java.util.List;
import java.util.Optional;

import com.orbit.organization.domain.CategoryName;
import com.orbit.organization.domain.MemberCategory;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.organization.domain.OrganizationId;

/** 직원 유형과 기사 유형은 저장소가 달라 식별자가 겹칠 수 있으므로 모든 조회에 종류를 함께 받는다. */
public interface MemberCategoryRepository {

    MemberCategory save(MemberCategory category);

    /** 삭제된 유형도 찾는다. 다른 발주사 유형인지는 호출자가 확인한다. */
    Optional<MemberCategory> findById(MemberCategoryKind kind, MemberCategoryId id);

    /** 같은 발주사·같은 종류에서 이름이 정확히 같은 삭제되지 않은 유형. */
    Optional<MemberCategory> findUndeletedByName(
            OrganizationId organizationId, MemberCategoryKind kind, CategoryName name);

    /** 같은 발주사·같은 종류의 삭제되지 않은 유형을 만든 순서대로 돌려준다(목록 순서는 잠정 — O-18). */
    List<MemberCategory> listUndeleted(OrganizationId organizationId, MemberCategoryKind kind);
}
