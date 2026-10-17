package com.orbit.schedule.application.port.out;

import java.util.Map;
import java.util.Set;

import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;

/**
 * 조직 직원 소속(총관리자·직원)의 표시 정보(이름·총관리자 여부·직원유형)를 찾는 출력 포트(ACL). 구현은 소속·직원유형을 organization 모듈의 공개 계약으로, 계정
 * 이름을 profile 모듈의 이름 조회 계약({@code ProfileLookup}, HM-305)으로 찾아 합친다. 인자는 null이 아니다.
 */
public interface LoadStaffProfilesPort {

    /**
     * 지정한 소속의 표시 정보를 소속 식별자별로 돌려준다. 활성 여부와 관계없이(비활성 소속·탈퇴한 계정 포함) 그 조직의 소속이면 담고, 다른 조직의 소속이거나
     * 찾을 수 없으면 결과에서 빠진다.
     */
    Map<MembershipId, StaffProfile> findProfiles(OrganizationId organizationId, Set<MembershipId> membershipIds);
}
