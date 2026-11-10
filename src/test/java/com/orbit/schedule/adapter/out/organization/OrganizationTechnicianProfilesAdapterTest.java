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
import com.orbit.organization.TechnicianDirectoryEntry;
import com.orbit.profile.ProfileLookup;
import com.orbit.schedule.application.port.out.TechnicianProfile;
import com.orbit.schedule.application.port.out.TypeLabel;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;

@DisplayName("organization 기사 계약·profile 이름을 기사 표시 정보로 번역")
class OrganizationTechnicianProfilesAdapterTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);

    private final MemberDirectory memberDirectory = mock(MemberDirectory.class);
    private final ProfileLookup profileLookup = mock(ProfileLookup.class);
    private final OrganizationTechnicianProfilesAdapter adapter =
            new OrganizationTechnicianProfilesAdapter(memberDirectory, profileLookup);

    @Test
    @DisplayName("기사 계약의 계정으로 이름을 찾아 기사 유형과 함께 계약별로 돌려주고, 이름이 없는 계정은 이름 없이 담는다")
    void combinesDirectoryAndNames() {
        // 역할 변경으로 끝난 계약도 organization이 돌려주면 이름이 붙는다.
        when(memberDirectory.findTechnicians(100L, Set.of(5L, 6L)))
                .thenReturn(List.of(
                        new TechnicianDirectoryEntry(5L, 11L, new MemberCategoryLabel(4L, "전기 기사", "#445566")),
                        new TechnicianDirectoryEntry(6L, 12L, null)));
        when(profileLookup.findNames(Set.of(11L, 12L))).thenReturn(Map.of(11L, "김태훈"));

        assertThat(adapter.findProfiles(ORGANIZATION_ID, Set.of(new TechnicianId(5L), new TechnicianId(6L))))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        new TechnicianId(5L),
                        new TechnicianProfile(
                                new TechnicianId(5L), "김태훈", new TypeLabel(4L, "전기 기사", "#445566", false)),
                        new TechnicianId(6L),
                        new TechnicianProfile(new TechnicianId(6L), null, null)));
    }

    @Test
    @DisplayName("활성 기사 계약 전부를 이름과 함께 돌려준다")
    void listsActiveTechniciansWithNames() {
        when(memberDirectory.listActiveTechnicians(100L))
                .thenReturn(List.of(new TechnicianDirectoryEntry(5L, 11L, null)));
        when(profileLookup.findNames(Set.of(11L))).thenReturn(Map.of(11L, "김태훈"));

        assertThat(adapter.listActive(ORGANIZATION_ID))
                .containsExactly(new TechnicianProfile(new TechnicianId(5L), "김태훈", null));
    }

    @Test
    @DisplayName("찾을 계약이 없으면 organization·profile을 부르지 않고, 활성 기사가 없으면 profile을 부르지 않는다")
    void skipsLookupWhenNothingToFind() {
        assertThat(adapter.findProfiles(ORGANIZATION_ID, Set.of())).isEmpty();
        verifyNoInteractions(memberDirectory, profileLookup);

        when(memberDirectory.listActiveTechnicians(100L)).thenReturn(List.of());
        assertThat(adapter.listActive(ORGANIZATION_ID)).isEmpty();
        verifyNoInteractions(profileLookup);
    }
}
