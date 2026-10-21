package com.orbit.schedule.adapter.out.organization;

import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.orbit.schedule.application.port.out.LoadStaffProfilesPort;
import com.orbit.schedule.application.port.out.StaffProfile;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;

/**
 * 임시 LoadStaffProfilesPort 구현. organization의 소속·직원유형 조회 계약(HM-288·HM-291)과 profile의 계정 이름 조회
 * 계약(HM-305)이 아직 없어 표시 정보가 없는 것으로 본다(빈 결과). 계약이 생기면 둘을 합쳐 호출하는 어댑터로 교체한 뒤 삭제한다.
 * {@code local}·{@code test}·{@code dev} 프로필에서만 등록하며 운영({@code prod})에는 등록하지 않는다.
 */
@Component
@Profile({"local & !prod", "test & !prod", "dev & !prod"})
class EmptyStaffProfilesAdapter implements LoadStaffProfilesPort {

    @Override
    public Map<MembershipId, StaffProfile> findProfiles(
            OrganizationId organizationId, Set<MembershipId> membershipIds) {
        return Map.of();
    }
}
