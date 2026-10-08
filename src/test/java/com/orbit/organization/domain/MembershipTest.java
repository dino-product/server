package com.orbit.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;

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
}
