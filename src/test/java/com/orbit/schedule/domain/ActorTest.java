package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("작업 행위자")
class ActorTest {

    private static final MembershipId MEMBERSHIP_ID = new MembershipId(1L);
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);

    @ParameterizedTest
    @CsvSource({"OWNER, true", "STAFF, true", "TECHNICIAN, false"})
    @DisplayName("작업 관리(등록·수정·배정·취소)는 총관리자와 직원만 할 수 있다")
    void onlyOwnerAndStaffCanManageWorks(ActorRole role, boolean expected) {
        assertThat(new Actor(MEMBERSHIP_ID, ORGANIZATION_ID, role).canManageWorks())
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"OWNER, false", "STAFF, false", "TECHNICIAN, true"})
    @DisplayName("작업 수행(수락·거절·시작·완료보고)은 기사만 할 수 있다")
    void onlyTechnicianCanPerformWorks(ActorRole role, boolean expected) {
        assertThat(new Actor(MEMBERSHIP_ID, ORGANIZATION_ID, role).canPerformWorks())
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("소속이 null이면 거부한다")
    void rejectsNullMembershipId() {
        assertThatThrownBy(() -> new Actor(null, ORGANIZATION_ID, ActorRole.STAFF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("membershipId must not be null");
    }

    @Test
    @DisplayName("조직이 null이면 거부한다")
    void rejectsNullOrganizationId() {
        assertThatThrownBy(() -> new Actor(MEMBERSHIP_ID, null, ActorRole.STAFF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("organizationId must not be null");
    }

    @Test
    @DisplayName("역할이 null이면 거부한다")
    void rejectsNullRole() {
        assertThatThrownBy(() -> new Actor(MEMBERSHIP_ID, ORGANIZATION_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("role must not be null");
    }
}
