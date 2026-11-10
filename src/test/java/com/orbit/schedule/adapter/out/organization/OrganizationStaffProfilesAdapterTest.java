package com.orbit.schedule.adapter.out.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.organization.MemberCategoryLabel;
import com.orbit.organization.MemberDirectory;
import com.orbit.organization.StaffDirectoryEntry;
import com.orbit.profile.ProfileLookup;
import com.orbit.schedule.application.port.out.StaffProfile;
import com.orbit.schedule.application.port.out.TypeLabel;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;

@DisplayName("organization 직원 소속·profile 이름을 등록자 표시 정보로 번역")
class OrganizationStaffProfilesAdapterTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);

    private final MemberDirectory memberDirectory = mock(MemberDirectory.class);
    private final ProfileLookup profileLookup = mock(ProfileLookup.class);
    private final OrganizationStaffProfilesAdapter adapter =
            new OrganizationStaffProfilesAdapter(memberDirectory, profileLookup);

    @Test
    @DisplayName("소속의 계정으로 이름을 찾아 총관리자 여부·직원 유형과 함께 소속별로 돌려주고, 이름이 없는 계정은 이름 없이 담는다")
    void combinesDirectoryAndNames() {
        when(memberDirectory.findStaff(100L, Set.of(1L, 2L)))
                .thenReturn(List.of(
                        new StaffDirectoryEntry(1L, 11L, true, new MemberCategoryLabel(3L, "관리자", "#112233")),
                        new StaffDirectoryEntry(2L, 12L, false, null)));
        when(profileLookup.findNames(Set.of(11L, 12L))).thenReturn(Map.of(11L, "김운영"));

        assertThat(adapter.findProfiles(ORGANIZATION_ID, Set.of(new MembershipId(1L), new MembershipId(2L))))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        new MembershipId(1L),
                        new StaffProfile(new MembershipId(1L), "김운영", true, new TypeLabel(3L, "관리자", "#112233", false)),
                        new MembershipId(2L),
                        new StaffProfile(new MembershipId(2L), null, false, null)));
    }

    @Test
    @DisplayName("organization이 돌려주지 않은 소속(다른 발주사·없는 소속)은 결과에서 빠진다")
    void omitsMembershipsTheDirectoryDoesNotReturn() {
        when(memberDirectory.findStaff(100L, Set.of(1L, 9L)))
                .thenReturn(List.of(new StaffDirectoryEntry(1L, 11L, false, null)));
        when(profileLookup.findNames(Set.of(11L))).thenReturn(Map.of(11L, "김운영"));

        assertThat(adapter.findProfiles(ORGANIZATION_ID, Set.of(new MembershipId(1L), new MembershipId(9L))))
                .containsOnlyKeys(new MembershipId(1L));
    }

    @Test
    @DisplayName("찾을 소속이 없으면 organization·profile을 부르지 않는다")
    void skipsLookupForEmptyMemberships() {
        assertThat(adapter.findProfiles(ORGANIZATION_ID, Set.of())).isEmpty();
        verifyNoInteractions(memberDirectory, profileLookup);
    }
}
