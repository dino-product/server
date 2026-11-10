package com.orbit.schedule.adapter.out.organization;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.orbit.organization.MemberDirectory;
import com.orbit.organization.TechnicianDirectoryEntry;
import com.orbit.profile.ProfileLookup;
import com.orbit.schedule.application.port.out.LoadTechnicianProfilesPort;
import com.orbit.schedule.application.port.out.TechnicianProfile;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;

/**
 * organization의 기사 계약 표시 정보({@link MemberDirectory})와 profile의 계정 이름({@link ProfileLookup})을 합쳐 기사 표시 정보로 옮기는 ACL 어댑터.
 * 비활성·역할 변경으로 끝난 계약도 담는 판정과 다른 발주사의 계약을 빼는 판정은 organization 계약이 맡는다. 이름을 찾지 못한 계정은 이름 없이 담는다.
 */
@Component
class OrganizationTechnicianProfilesAdapter implements LoadTechnicianProfilesPort {

    private final MemberDirectory memberDirectory;
    private final ProfileLookup profileLookup;

    OrganizationTechnicianProfilesAdapter(MemberDirectory memberDirectory, ProfileLookup profileLookup) {
        this.memberDirectory = memberDirectory;
        this.profileLookup = profileLookup;
    }

    @Override
    public List<TechnicianProfile> listActive(OrganizationId organizationId) {
        return withNames(memberDirectory.listActiveTechnicians(organizationId.value()));
    }

    @Override
    public Map<TechnicianId, TechnicianProfile> findProfiles(
            OrganizationId organizationId, Set<TechnicianId> technicianIds) {
        if (technicianIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = technicianIds.stream().map(TechnicianId::value).collect(Collectors.toSet());
        return withNames(memberDirectory.findTechnicians(organizationId.value(), ids)).stream()
                .collect(Collectors.toUnmodifiableMap(TechnicianProfile::technicianId, profile -> profile));
    }

    private List<TechnicianProfile> withNames(List<TechnicianDirectoryEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = profileLookup.findNames(
                entries.stream().map(TechnicianDirectoryEntry::accountId).collect(Collectors.toSet()));
        return entries.stream()
                .map(entry -> new TechnicianProfile(
                        new TechnicianId(entry.technicianId()),
                        names.get(entry.accountId()),
                        MemberCategoryLabels.toTypeLabel(entry.technicianCategory())))
                .toList();
    }
}
