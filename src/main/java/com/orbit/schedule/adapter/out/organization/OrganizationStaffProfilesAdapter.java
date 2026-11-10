package com.orbit.schedule.adapter.out.organization;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.orbit.organization.MemberDirectory;
import com.orbit.organization.StaffDirectoryEntry;
import com.orbit.profile.ProfileLookup;
import com.orbit.schedule.application.port.out.LoadStaffProfilesPort;
import com.orbit.schedule.application.port.out.StaffProfile;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;

/**
 * organization의 직원 소속 표시 정보({@link MemberDirectory})와 profile의 계정 이름({@link ProfileLookup})을 합쳐 등록자 표시 정보로 옮기는 ACL
 * 어댑터. 비활성·역할 변경으로 끝난 소속도 담는 판정과 다른 발주사의 소속을 빼는 판정은 organization 계약이 맡는다. 이름을 찾지 못한 계정은 이름 없이 담는다.
 */
@Component
class OrganizationStaffProfilesAdapter implements LoadStaffProfilesPort {

    private final MemberDirectory memberDirectory;
    private final ProfileLookup profileLookup;

    OrganizationStaffProfilesAdapter(MemberDirectory memberDirectory, ProfileLookup profileLookup) {
        this.memberDirectory = memberDirectory;
        this.profileLookup = profileLookup;
    }

    @Override
    public Map<MembershipId, StaffProfile> findProfiles(
            OrganizationId organizationId, Set<MembershipId> membershipIds) {
        if (membershipIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = membershipIds.stream().map(MembershipId::value).collect(Collectors.toSet());
        List<StaffDirectoryEntry> entries = memberDirectory.findStaff(organizationId.value(), ids);
        Map<Long, String> names = profileLookup.findNames(
                entries.stream().map(StaffDirectoryEntry::accountId).collect(Collectors.toSet()));
        return entries.stream()
                .collect(Collectors.toUnmodifiableMap(
                        entry -> new MembershipId(entry.membershipId()),
                        entry -> new StaffProfile(
                                new MembershipId(entry.membershipId()),
                                names.get(entry.accountId()),
                                entry.owner(),
                                MemberCategoryLabels.toTypeLabel(entry.staffCategory()))));
    }
}
