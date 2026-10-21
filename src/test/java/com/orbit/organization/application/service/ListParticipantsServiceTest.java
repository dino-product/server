package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.query.dto.ListParticipantsQuery;
import com.orbit.organization.application.port.in.query.dto.ParticipantInfo;
import com.orbit.organization.application.port.in.query.dto.ParticipantsInfo;
import com.orbit.organization.application.port.out.AccountNamesPort;
import com.orbit.organization.application.port.out.ParticipantQueryPort;
import com.orbit.organization.application.port.out.ParticipantRow;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MemberRole;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.shared.error.BaseCode;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("참여 인원 목록 조회")
class ListParticipantsServiceTest {

    private static final Instant FIRST = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-10-02T00:00:00Z");
    private static final Instant THIRD = Instant.parse("2026-10-03T00:00:00Z");
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(10L);
    private static final AccountId OWNER = new AccountId(1L);
    private static final AccountId STAFF = new AccountId(2L);

    @Mock
    private ParticipantQueryPort queryPort;

    @Mock
    private AccountNamesPort accountNames;

    private ListParticipantsService service;

    @BeforeEach
    void setUp() {
        InMemoryMembershipRepository membershipRepository = new InMemoryMembershipRepository();
        membershipRepository.given(ORGANIZATION_ID, OWNER, true);
        membershipRepository.given(ORGANIZATION_ID, STAFF, false);
        service = new ListParticipantsService(membershipRepository, queryPort, accountNames);
    }

    @Test
    @DisplayName("총관리자는 직원 소속·기사 계약을 이름·역할·세부 유형·상태·참여일과 함께 이름 가나다순으로 받는다")
    void listsParticipantsSortedByName() {
        when(queryPort.listParticipants(ORGANIZATION_ID))
                .thenReturn(List.of(
                        staff(1L, OWNER, MemberRole.OWNER, true, FIRST, FIRST),
                        technician(5L, new AccountId(3L), false, SECOND, THIRD, 7L, "에어컨", "#FF0000"),
                        staff(2L, STAFF, MemberRole.STAFF, true, SECOND, SECOND)));
        when(accountNames.findNames(Set.of(OWNER, STAFF, new AccountId(3L))))
                .thenReturn(Map.of(OWNER, "하총괄", STAFF, "김직원", new AccountId(3L), "나기사"));

        ParticipantsInfo result = service.list(query(null, null));

        assertThat(result.participants())
                .containsExactly(
                        new ParticipantInfo(2L, STAFF.value(), "김직원", MemberRole.STAFF, true, null, SECOND),
                        new ParticipantInfo(
                                5L,
                                3L,
                                "나기사",
                                MemberRole.TECHNICIAN,
                                false,
                                new ParticipantInfo.Category(7L, "에어컨", "#FF0000"),
                                SECOND),
                        new ParticipantInfo(1L, OWNER.value(), "하총괄", MemberRole.OWNER, true, null, FIRST));
        assertThat(result.counts()).isEqualTo(new ParticipantsInfo.Counts(3, 2, 1));
    }

    @Test
    @DisplayName("역할 변경으로 두 행이 있는 계정은 활성 행, 둘 다 비활성이면 상태가 늦게 바뀐 행 한 줄만 보이고 시각이 같으면 직원 소속을 보인다")
    void showsOneRowPerAccount() {
        AccountId active = new AccountId(3L);
        AccountId inactive = new AccountId(4L);
        AccountId tie = new AccountId(5L);
        when(queryPort.listParticipants(ORGANIZATION_ID))
                .thenReturn(List.of(
                        staff(11L, active, MemberRole.STAFF, false, FIRST, THIRD),
                        technician(21L, active, true, FIRST, SECOND, null, null, null),
                        staff(12L, inactive, MemberRole.STAFF, false, FIRST, SECOND),
                        technician(22L, inactive, false, FIRST, THIRD, null, null, null),
                        staff(13L, tie, MemberRole.STAFF, false, FIRST, SECOND),
                        technician(23L, tie, false, FIRST, SECOND, null, null, null)));
        when(accountNames.findNames(any())).thenReturn(Map.of(active, "가", inactive, "나", tie, "다"));

        assertThat(service.list(query(null, null)).participants())
                .extracting(ParticipantInfo::memberId, ParticipantInfo::role)
                .containsExactly(
                        tuple(21L, MemberRole.TECHNICIAN),
                        tuple(22L, MemberRole.TECHNICIAN),
                        tuple(13L, MemberRole.STAFF));
    }

    @Test
    @DisplayName("같은 이름은 참여일이 오래된 순이고, 이름이 없는 계정은 맨 뒤에 둔다")
    void ordersSameNameByJoinedAtAndNamelessLast() {
        AccountId later = new AccountId(3L);
        AccountId earlier = new AccountId(4L);
        AccountId nameless = new AccountId(5L);
        when(queryPort.listParticipants(ORGANIZATION_ID))
                .thenReturn(List.of(
                        staff(13L, nameless, MemberRole.STAFF, true, FIRST, FIRST),
                        staff(11L, later, MemberRole.STAFF, true, THIRD, THIRD),
                        staff(12L, earlier, MemberRole.STAFF, true, SECOND, SECOND)));
        when(accountNames.findNames(any())).thenReturn(Map.of(later, "홍길동", earlier, "홍길동"));

        assertThat(service.list(query(null, null)).participants())
                .extracting(ParticipantInfo::memberId)
                .containsExactly(12L, 11L, 13L);
    }

    @Test
    @DisplayName("영문 이름은 대소문자 구분 없이 정렬하고, 대소문자만 다른 이름은 같은 이름으로 보아 참여일이 오래된 순이다")
    void ordersLatinNamesIgnoringCase() {
        AccountId zed = new AccountId(3L);
        AccountId amy = new AccountId(4L);
        AccountId bob = new AccountId(5L);
        AccountId upperAmy = new AccountId(6L);
        when(queryPort.listParticipants(ORGANIZATION_ID))
                .thenReturn(List.of(
                        staff(11L, zed, MemberRole.STAFF, true, FIRST, FIRST),
                        staff(12L, amy, MemberRole.STAFF, true, THIRD, THIRD),
                        staff(13L, bob, MemberRole.STAFF, true, FIRST, FIRST),
                        staff(14L, upperAmy, MemberRole.STAFF, true, SECOND, SECOND)));
        when(accountNames.findNames(any())).thenReturn(Map.of(zed, "Zed", amy, "amy", bob, "Bob", upperAmy, "AMY"));

        assertThat(service.list(query(null, null)).participants())
                .extracting(ParticipantInfo::memberId)
                .containsExactly(14L, 12L, 13L, 11L);
    }

    @Test
    @DisplayName("역할 필터는 사무실 직원(총관리자 포함)이나 기사만 남기고, 인원 수는 필터와 관계없이 역할별로 센다")
    void filtersByTypeAndCountsEveryRole() {
        givenOwnerStaffTechnician();

        ParticipantsInfo staffOnly = service.list(query(ParticipationType.STAFF, null));
        ParticipantsInfo technicianOnly = service.list(query(ParticipationType.TECHNICIAN, null));

        assertThat(staffOnly.participants())
                .extracting(ParticipantInfo::role)
                .containsExactlyInAnyOrder(MemberRole.OWNER, MemberRole.STAFF);
        assertThat(technicianOnly.participants())
                .extracting(ParticipantInfo::role)
                .containsExactly(MemberRole.TECHNICIAN);
        assertThat(staffOnly.counts()).isEqualTo(new ParticipantsInfo.Counts(3, 2, 1));
        assertThat(technicianOnly.counts()).isEqualTo(new ParticipantsInfo.Counts(3, 2, 1));
    }

    @Test
    @DisplayName("이름 검색은 앞뒤 공백을 지우고 대소문자 구분 없이 부분 일치로 거르며, 인원 수도 검색 결과로 센다")
    void searchesNameByContainmentIgnoringCase() {
        givenOwnerStaffTechnician();

        ParticipantsInfo result = service.list(query(null, "  kim "));

        assertThat(result.participants()).extracting(ParticipantInfo::name).containsExactly("Kim Staff", "OKIM");
        assertThat(result.counts()).isEqualTo(new ParticipantsInfo.Counts(2, 2, 0));
    }

    @Test
    @DisplayName("빈 검색어는 거르지 않고, 검색어가 있으면 이름 없는 계정은 나오지 않는다")
    void blankSearchKeepsAllAndSearchSkipsNameless() {
        givenOwnerStaffTechnician();

        assertThat(service.list(query(null, "   ")).participants()).hasSize(3);
        assertThat(service.list(query(null, "Lee")).participants()).isEmpty();
    }

    @Test
    @DisplayName("구성원이 없으면 이름을 찾지 않는다")
    void skipsNameLookupWithoutParticipants() {
        when(queryPort.listParticipants(ORGANIZATION_ID)).thenReturn(List.of());

        ParticipantsInfo result = service.list(query(null, null));

        assertThat(result.participants()).isEmpty();
        assertThat(result.counts()).isEqualTo(new ParticipantsInfo.Counts(0, 0, 0));
        verifyNoInteractions(accountNames);
    }

    @Test
    @DisplayName("총관리자가 아닌 직원은 조회할 수 없다")
    void rejectsStaff() {
        assertRejected(STAFF.value(), ORGANIZATION_ID.value(), OrganizationErrorCode.OWNER_ONLY);
    }

    @Test
    @DisplayName("그 발주사의 활성 직원 소속이 없으면(기사·다른 발주사·없는 발주사) 조회할 수 없다")
    void rejectsNonMember() {
        assertRejected(9L, ORGANIZATION_ID.value(), OrganizationErrorCode.NOT_ORGANIZATION_MEMBER);
        assertRejected(OWNER.value(), 11L, OrganizationErrorCode.NOT_ORGANIZATION_MEMBER);
    }

    @Test
    @DisplayName("0 이하의 발주사 식별자는 입력 오류다")
    void rejectsNonPositiveOrganizationId() {
        assertRejected(OWNER.value(), 0L, CommonErrorCode.BAD_REQUEST);
    }

    private void givenOwnerStaffTechnician() {
        AccountId technician = new AccountId(3L);
        when(queryPort.listParticipants(ORGANIZATION_ID))
                .thenReturn(List.of(
                        staff(1L, OWNER, MemberRole.OWNER, true, FIRST, FIRST),
                        staff(2L, STAFF, MemberRole.STAFF, true, SECOND, SECOND),
                        technician(5L, technician, true, THIRD, THIRD, null, null, null)));
        when(accountNames.findNames(Set.of(OWNER, STAFF, technician)))
                .thenReturn(Map.of(OWNER, "OKIM", STAFF, "Kim Staff"));
    }

    private void assertRejected(Long accountId, Long organizationId, BaseCode expected) {
        assertThatThrownBy(() -> service.list(new ListParticipantsQuery(accountId, organizationId, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(expected));
        verifyNoInteractions(queryPort);
    }

    private static ListParticipantsQuery query(ParticipationType type, String name) {
        return new ListParticipantsQuery(OWNER.value(), ORGANIZATION_ID.value(), type, name);
    }

    private static ParticipantRow staff(
            long membershipId,
            AccountId accountId,
            MemberRole role,
            boolean active,
            Instant joinedAt,
            Instant statusChangedAt) {
        return new ParticipantRow(role, membershipId, accountId, active, null, null, null, joinedAt, statusChangedAt);
    }

    private static ParticipantRow technician(
            long technicianId,
            AccountId accountId,
            boolean active,
            Instant joinedAt,
            Instant statusChangedAt,
            Long categoryId,
            String categoryName,
            String categoryColor) {
        return new ParticipantRow(
                MemberRole.TECHNICIAN,
                technicianId,
                accountId,
                active,
                categoryId,
                categoryName,
                categoryColor,
                joinedAt,
                statusChangedAt);
    }
}
