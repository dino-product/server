package com.orbit.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("직원 소속 도메인")
class MembershipTest {

    private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");

    @Test
    @DisplayName("발주사 생성자의 소속은 총관리자 표시가 있는 활성 직원 소속이다")
    void founderMembershipIsActiveOwner() {
        Membership membership = Membership.founder(new OrganizationId(1L), new AccountId(7L), NOW);

        assertThat(membership.id()).isEmpty();
        assertThat(membership.organizationId()).isEqualTo(new OrganizationId(1L));
        assertThat(membership.accountId()).isEqualTo(new AccountId(7L));
        assertThat(membership.isOwner()).isTrue();
        assertThat(membership.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(membership.joinedAt()).isEqualTo(NOW);
        assertThat(membership.statusChangedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("직원 소속을 총관리자로 지정하면 총관리자 표시만 붙고 나머지는 그대로다")
    void designatesStaffAsOwner() {
        Membership staff = staff();

        Membership designated = staff.designateAsOwner();

        assertThat(designated.isOwner()).isTrue();
        assertThat(designated.id()).isEqualTo(staff.id());
        assertThat(designated.organizationId()).isEqualTo(staff.organizationId());
        assertThat(designated.accountId()).isEqualTo(staff.accountId());
        assertThat(designated.status()).isEqualTo(staff.status());
        assertThat(designated.joinedAt()).isEqualTo(staff.joinedAt());
        assertThat(designated.statusChangedAt()).isEqualTo(staff.statusChangedAt());
    }

    @Test
    @DisplayName("이미 총관리자인 소속을 다시 지정하면 바뀌지 않는다")
    void designatingOwnerAgainLeavesItUnchanged() {
        Membership owner = staff().designateAsOwner();

        assertThat(owner.designateAsOwner()).isSameAs(owner);
    }

    @Test
    @DisplayName("비활성 직원 소속은 총관리자로 지정할 수 없다")
    void rejectsDesignatingDeactivatedMembership() {
        Membership deactivated = Membership.reconstitute(
                new MembershipId(3L),
                new OrganizationId(1L),
                new AccountId(8L),
                false,
                MembershipStatus.DEACTIVATED,
                NOW,
                NOW.plusSeconds(60));

        assertThatThrownBy(deactivated::designateAsOwner).isInstanceOf(InactiveMembershipException.class);
    }

    @Test
    @DisplayName("다른 총관리자가 남아 있으면 총관리자 표시를 뗀다")
    void revokesOwnerWhileAnotherOwnerRemains() {
        Membership owner = staff().designateAsOwner();

        Membership revoked = owner.revokeOwner(2);

        assertThat(revoked.isOwner()).isFalse();
        assertThat(revoked.id()).isEqualTo(owner.id());
        assertThat(revoked.status()).isEqualTo(owner.status());
    }

    @Test
    @DisplayName("발주사의 마지막 총관리자는 해제할 수 없다")
    void rejectsRevokingLastOwner() {
        Membership owner = staff().designateAsOwner();

        assertThatThrownBy(() -> owner.revokeOwner(1)).isInstanceOf(LastOwnerException.class);
    }

    @Test
    @DisplayName("총관리자가 아닌 소속을 해제하면 바뀌지 않는다")
    void revokingStaffLeavesItUnchanged() {
        Membership staff = staff();

        assertThat(staff.revokeOwner(1)).isSameAs(staff);
    }

    private static Membership staff() {
        return Membership.reconstitute(
                new MembershipId(3L),
                new OrganizationId(1L),
                new AccountId(8L),
                false,
                MembershipStatus.ACTIVE,
                NOW,
                NOW.plusSeconds(60));
    }
}
