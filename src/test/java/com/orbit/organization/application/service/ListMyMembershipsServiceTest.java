package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.organization.application.port.in.query.dto.ListMyMembershipsQuery;
import com.orbit.organization.application.port.in.query.dto.MyMembershipInfo;
import com.orbit.organization.application.port.out.AccountMembership;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MemberRole;
import com.orbit.organization.domain.OrganizationId;

@DisplayName("내 소속 목록 조회")
class ListMyMembershipsServiceTest {

    private static final long ACCOUNT_ID = 7L;
    private static final Instant DAY_1 = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant DAY_2 = Instant.parse("2026-10-02T00:00:00Z");
    private static final Instant DAY_3 = Instant.parse("2026-10-03T00:00:00Z");
    private static final Instant DAY_4 = Instant.parse("2026-10-04T00:00:00Z");

    private final List<AccountMembership> rows = new ArrayList<>();
    private final List<AccountId> requestedAccounts = new ArrayList<>();

    private final ListMyMembershipsService service = new ListMyMembershipsService(accountId -> {
        requestedAccounts.add(accountId);
        return List.copyOf(rows);
    });

    @Test
    @DisplayName("소속이 없으면 빈 목록이다")
    void returnsEmptyListWithoutMembership() {
        assertThat(list()).isEmpty();
    }

    @Test
    @DisplayName("요청자 계정의 소속만 읽는다")
    void readsOnlyRequesterAccount() {
        list();

        assertThat(requestedAccounts).containsExactly(new AccountId(ACCOUNT_ID));
    }

    @Test
    @DisplayName("소속 하나를 발주사명·역할·상태와 함께 돌려준다")
    void returnsSingleMembershipWithOrganizationNameRoleAndStatus() {
        rows.add(row(10L, "오르빗 설비", MemberRole.OWNER, true, DAY_1, DAY_1));

        assertThat(list()).containsExactly(new MyMembershipInfo(10L, "오르빗 설비", MemberRole.OWNER, true));
    }

    @Test
    @DisplayName("총관리자·직원·기사 소속을 발주사에 처음 참여한 순서로 모두 돌려준다")
    void listsOwnerStaffAndTechnicianInJoinedOrder() {
        rows.add(row(30L, "세 번째", MemberRole.TECHNICIAN, true, DAY_3, DAY_3));
        rows.add(row(10L, "첫 번째", MemberRole.OWNER, true, DAY_1, DAY_1));
        rows.add(row(20L, "두 번째", MemberRole.STAFF, true, DAY_2, DAY_2));

        assertThat(list())
                .containsExactly(
                        new MyMembershipInfo(10L, "첫 번째", MemberRole.OWNER, true),
                        new MyMembershipInfo(20L, "두 번째", MemberRole.STAFF, true),
                        new MyMembershipInfo(30L, "세 번째", MemberRole.TECHNICIAN, true));
    }

    @Test
    @DisplayName("비활성 소속도 비활성 상태로 보여 준다")
    void showsDeactivatedMembershipAsInactive() {
        rows.add(row(10L, "오르빗 설비", MemberRole.STAFF, false, DAY_1, DAY_2));

        assertThat(list()).containsExactly(new MyMembershipInfo(10L, "오르빗 설비", MemberRole.STAFF, false));
    }

    @Test
    @DisplayName("역할 변경으로 비활성이 된 행은 빼고 같은 발주사의 활성 행만 보여 준다")
    void hidesRowEndedByRoleChange() {
        rows.add(row(10L, "오르빗 설비", MemberRole.TECHNICIAN, false, DAY_1, DAY_3));
        rows.add(row(10L, "오르빗 설비", MemberRole.STAFF, true, DAY_3, DAY_3));

        assertThat(list()).containsExactly(new MyMembershipInfo(10L, "오르빗 설비", MemberRole.STAFF, true));
    }

    @Test
    @DisplayName("한 발주사의 행이 모두 비활성이면 상태가 가장 늦게 바뀐 행 하나만 보여 준다")
    void showsLatestDeactivatedRowWhenNoRowIsActive() {
        rows.add(row(10L, "오르빗 설비", MemberRole.TECHNICIAN, false, DAY_1, DAY_2));
        rows.add(row(10L, "오르빗 설비", MemberRole.STAFF, false, DAY_2, DAY_4));

        assertThat(list()).containsExactly(new MyMembershipInfo(10L, "오르빗 설비", MemberRole.STAFF, false));
    }

    @Test
    @DisplayName("역할을 바꿔도 발주사에 처음 참여한 시각으로 순서를 정한다")
    void keepsFirstJoinedOrderAfterRoleChange() {
        rows.add(row(10L, "먼저 참여", MemberRole.TECHNICIAN, false, DAY_1, DAY_4));
        rows.add(row(20L, "나중 참여", MemberRole.STAFF, true, DAY_2, DAY_2));
        rows.add(row(10L, "먼저 참여", MemberRole.STAFF, true, DAY_4, DAY_4));

        assertThat(list()).extracting(MyMembershipInfo::organizationId).containsExactly(10L, 20L);
    }

    @Test
    @DisplayName("한 발주사에 활성 직원 소속과 활성 기사 계약이 함께 있으면 불변식 위반으로 실패한다")
    void rejectsTwoActiveRowsInSameOrganization() {
        rows.add(row(10L, "오르빗 설비", MemberRole.STAFF, true, DAY_1, DAY_1));
        rows.add(row(10L, "오르빗 설비", MemberRole.TECHNICIAN, true, DAY_2, DAY_2));

        assertThatThrownBy(this::list).isInstanceOf(IllegalStateException.class);
    }

    private List<MyMembershipInfo> list() {
        return service.list(new ListMyMembershipsQuery(ACCOUNT_ID));
    }

    private static AccountMembership row(
            long organizationId,
            String organizationName,
            MemberRole role,
            boolean active,
            Instant joinedAt,
            Instant statusChangedAt) {
        return new AccountMembership(
                new OrganizationId(organizationId), organizationName, role, active, joinedAt, statusChangedAt);
    }
}
