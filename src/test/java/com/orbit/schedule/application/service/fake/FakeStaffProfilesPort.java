package com.orbit.schedule.application.service.fake;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.orbit.schedule.application.port.out.LoadStaffProfilesPort;
import com.orbit.schedule.application.port.out.StaffProfile;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;

/** 서비스 단위 테스트용 LoadStaffProfilesPort. 스캔되지 않도록 애너테이션을 붙이지 않는다. 테스트가 넣은 조직별 소속 표시 정보만 돌려준다. */
public class FakeStaffProfilesPort implements LoadStaffProfilesPort {

    private final Map<OrganizationId, Map<MembershipId, StaffProfile>> profiles = new HashMap<>();

    public void given(OrganizationId organizationId, StaffProfile profile) {
        profiles.computeIfAbsent(organizationId, ignored -> new HashMap<>()).put(profile.membershipId(), profile);
    }

    @Override
    public Map<MembershipId, StaffProfile> findProfiles(
            OrganizationId organizationId, Set<MembershipId> membershipIds) {
        Objects.requireNonNull(organizationId, "fake port received null organizationId");
        Map<MembershipId, StaffProfile> ofOrganization = profiles.getOrDefault(organizationId, Map.of());
        Map<MembershipId, StaffProfile> found = new HashMap<>();
        for (MembershipId membershipId : membershipIds) {
            StaffProfile profile = ofOrganization.get(membershipId);
            if (profile != null) {
                found.put(membershipId, profile);
            }
        }
        return Map.copyOf(found);
    }
}
