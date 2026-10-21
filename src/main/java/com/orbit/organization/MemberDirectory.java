package com.orbit.organization;

import java.util.List;
import java.util.Set;

/**
 * 발주사 구성원의 표시 정보 공개 조회. 다른 모듈이 직원 소속·기사 계약 식별자로 참조한 구성원을 화면에 보여 줄 때 쓴다. 계정 이름은 이 모듈이 갖지 않으므로
 * 계정 식별자를 돌려주고 이름은 호출자가 찾는다. 직원·기사 유형은 삭제되지 않은 유형만 담고, 유형이 없거나 삭제됐으면 {@code null}이다(삭제된 직원·기사 유형은
 * 소속을 유형 없음으로 둔다). 판단이 아닌 표시용이라 잠그지 않는다. 인자는 null이 아니고 식별자는 양수이며 어기면 {@link IllegalArgumentException}이다.
 */
public interface MemberDirectory {

    /**
     * 지정한 직원 소속(총관리자 포함)을 활성 여부와 관계없이(비활성·역할 변경으로 끝난 행 포함) 돌려준다. 다른 발주사의 소속과 없는 소속은 빠지며 순서는 보장하지
     * 않는다.
     */
    List<StaffDirectoryEntry> findStaff(Long organizationId, Set<Long> membershipIds);

    /** 지정한 기사 계약을 활성 여부와 관계없이 돌려준다. 다른 발주사의 계약과 없는 계약은 빠지며 순서는 보장하지 않는다. */
    List<TechnicianDirectoryEntry> findTechnicians(Long organizationId, Set<Long> technicianIds);

    /** 그 발주사의 활성 기사 계약 전부. 순서는 보장하지 않는다. */
    List<TechnicianDirectoryEntry> listActiveTechnicians(Long organizationId);
}
