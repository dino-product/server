package com.orbit.organization.application.service;

import static com.orbit.organization.application.service.MemberCategoryFixtures.ORGANIZATION_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.MemberCategoryLabel;
import com.orbit.organization.StaffDirectoryEntry;
import com.orbit.organization.TechnicianDirectoryEntry;
import com.orbit.organization.application.port.out.DirectoryCategory;
import com.orbit.organization.application.port.out.DirectoryStaff;
import com.orbit.organization.application.port.out.DirectoryTechnician;
import com.orbit.organization.application.port.out.MemberDirectoryQueryPort;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.TechnicianId;

@ExtendWith(MockitoExtension.class)
@DisplayName("구성원 표시 정보 공개 조회")
class MemberDirectoryServiceTest {

    @Mock
    private MemberDirectoryQueryPort queryPort;

    private MemberDirectoryService service;

    @BeforeEach
    void setUp() {
        service = new MemberDirectoryService(queryPort);
    }

    @Test
    @DisplayName("직원 소속을 계정·총관리자 여부·직원 유형과 함께 돌려주고 유형이 없으면 null이다")
    void findsStaff() {
        when(queryPort.findStaff(ORGANIZATION_ID, Set.of(new MembershipId(3L), new MembershipId(4L))))
                .thenReturn(List.of(
                        new DirectoryStaff(
                                new MembershipId(3L),
                                new AccountId(7L),
                                true,
                                new DirectoryCategory(new MemberCategoryId(5L), "정규직", "#3B82F6")),
                        new DirectoryStaff(new MembershipId(4L), new AccountId(8L), false, null)));

        assertThat(service.findStaff(10L, Set.of(3L, 4L)))
                .containsExactly(
                        new StaffDirectoryEntry(3L, 7L, true, new MemberCategoryLabel(5L, "정규직", "#3B82F6")),
                        new StaffDirectoryEntry(4L, 8L, false, null));
    }

    @Test
    @DisplayName("기사 계약과 활성 기사 목록을 계정·기사 유형과 함께 돌려준다")
    void findsTechnicians() {
        DirectoryTechnician technician = new DirectoryTechnician(
                new TechnicianId(21L),
                new AccountId(7L),
                new DirectoryCategory(new MemberCategoryId(5L), "전기 기사", "#EF4444"));
        when(queryPort.findTechnicians(ORGANIZATION_ID, Set.of(new TechnicianId(21L))))
                .thenReturn(List.of(technician));
        when(queryPort.listActiveTechnicians(ORGANIZATION_ID)).thenReturn(List.of(technician));
        TechnicianDirectoryEntry expected =
                new TechnicianDirectoryEntry(21L, 7L, new MemberCategoryLabel(5L, "전기 기사", "#EF4444"));

        assertThat(service.findTechnicians(10L, Set.of(21L))).containsExactly(expected);
        assertThat(service.listActiveTechnicians(10L)).containsExactly(expected);
    }

    @Test
    @DisplayName("찾을 식별자가 없으면 조회하지 않고 빈 목록이다")
    void emptyWithoutIds() {
        assertThat(service.findStaff(10L, Set.of())).isEmpty();
        assertThat(service.findTechnicians(10L, Set.of())).isEmpty();
        verifyNoInteractions(queryPort);
    }

    @Test
    @DisplayName("식별자가 양수가 아니거나 null이면 호출자 오류다")
    void rejectsInvalidArguments() {
        assertThatThrownBy(() -> service.findStaff(0L, Set.of(3L))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findStaff(10L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findTechnicians(10L, Set.of(-1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listActiveTechnicians(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
