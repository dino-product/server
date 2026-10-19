package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.dto.DeactivateWithdrawnAccountCommand;
import com.orbit.organization.application.port.out.OrganizationBusyException;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.Technician;
import com.orbit.organization.domain.TechnicianStatus;
import com.orbit.shared.error.BusinessException;

@DisplayName("탈퇴한 계정의 소속 비활성화")
class DeactivateWithdrawnAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T00:00:00Z");
    private static final AccountId WITHDRAWN = new AccountId(7L);
    private static final AccountId OTHER = new AccountId(8L);
    private static final OrganizationId FIRST = new OrganizationId(1L);
    private static final OrganizationId SECOND = new OrganizationId(2L);
    private static final OrganizationId THIRD = new OrganizationId(3L);

    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final InMemoryTechnicianRepository technicians = new InMemoryTechnicianRepository();

    private DeactivateWithdrawnAccountService service;

    @BeforeEach
    void setUp() {
        service = new DeactivateWithdrawnAccountService(
                organizationRepository, memberships, technicians, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("모든 발주사의 활성 직원 소속(총관리자 포함)과 기사 계약을 비활성으로 바꾸고 탈퇴 표시를 남긴다")
    void deactivatesEveryActiveMembershipAndTechnician() {
        Membership owner = memberships.given(FIRST, WITHDRAWN, true);
        Membership staff = memberships.given(SECOND, WITHDRAWN, false);
        Technician technician = technicians.given(THIRD, WITHDRAWN, TechnicianStatus.ACTIVE);

        deactivate();

        assertThat(memberships.stored(owner.id().orElseThrow())).satisfies(withdrawn -> {
            assertThat(withdrawn.status()).isEqualTo(MembershipStatus.DEACTIVATED);
            assertThat(withdrawn.statusChangedAt()).isEqualTo(NOW);
            assertThat(withdrawn.withdrawnAt()).contains(NOW);
        });
        assertThat(memberships.stored(staff.id().orElseThrow()).withdrawnAt()).contains(NOW);
        assertThat(technicians.stored(technician.id().orElseThrow())).satisfies(withdrawn -> {
            assertThat(withdrawn.status()).isEqualTo(TechnicianStatus.DEACTIVATED);
            assertThat(withdrawn.withdrawnAt()).contains(NOW);
        });
    }

    @Test
    @DisplayName("직원 소속·기사 계약이 있는 발주사를 한 번씩 식별자 오름차순으로 잠근 뒤 바꾼다")
    void locksEachOrganizationOnceInIdOrder() {
        memberships.given(THIRD, WITHDRAWN, false);
        memberships.given(FIRST, WITHDRAWN, false, MembershipStatus.DEACTIVATED);
        technicians.given(FIRST, WITHDRAWN, TechnicianStatus.ACTIVE);
        technicians.given(SECOND, WITHDRAWN, TechnicianStatus.ACTIVE);

        deactivate();

        InOrder locks = inOrder(organizationRepository);
        locks.verify(organizationRepository).lock(FIRST);
        locks.verify(organizationRepository).lock(SECOND);
        locks.verify(organizationRepository).lock(THIRD);
        verify(organizationRepository, times(3)).lock(any());
    }

    @Test
    @DisplayName("이미 비활성인 행은 상태와 상태 변경 시각을 그대로 두고 탈퇴 표시만 남긴다")
    void marksAlreadyDeactivatedRowsWithoutChangingStatus() {
        Membership deactivated = memberships.given(FIRST, WITHDRAWN, false, MembershipStatus.DEACTIVATED);
        Technician technician = technicians.given(FIRST, WITHDRAWN, TechnicianStatus.DEACTIVATED);

        deactivate();

        assertThat(memberships.stored(deactivated.id().orElseThrow())).satisfies(withdrawn -> {
            assertThat(withdrawn.statusChangedAt()).isEqualTo(deactivated.statusChangedAt());
            assertThat(withdrawn.withdrawnAt()).contains(NOW);
        });
        assertThat(technicians.stored(technician.id().orElseThrow()).withdrawnAt())
                .contains(NOW);
    }

    @Test
    @DisplayName("같은 탈퇴를 다시 받으면 아무것도 바꾸지 않고 성공한다")
    void redeliveryChangesNothing() {
        Membership owner = memberships.given(FIRST, WITHDRAWN, true);
        technicians.given(SECOND, WITHDRAWN, TechnicianStatus.ACTIVE);
        deactivate();
        int membershipSaves = memberships.saveCount();
        int technicianSaves = technicians.saveCount();

        deactivate();

        assertThat(memberships.saveCount()).isEqualTo(membershipSaves);
        assertThat(technicians.saveCount()).isEqualTo(technicianSaves);
        assertThat(memberships.stored(owner.id().orElseThrow()).withdrawnAt()).contains(NOW);
    }

    @Test
    @DisplayName("다른 계정의 소속·계약은 바꾸지 않는다")
    void leavesOtherAccountsUntouched() {
        memberships.given(FIRST, WITHDRAWN, false);
        Membership other = memberships.given(FIRST, OTHER, true);
        Technician otherTechnician = technicians.given(FIRST, OTHER, TechnicianStatus.ACTIVE);

        deactivate();

        assertThat(memberships.stored(other.id().orElseThrow()).status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(memberships.stored(other.id().orElseThrow()).isWithdrawn()).isFalse();
        assertThat(technicians.stored(otherTechnician.id().orElseThrow()).isWithdrawn())
                .isFalse();
    }

    @Test
    @DisplayName("소속·계약이 없는 계정은 발주사를 잠그지 않고 성공한다")
    void succeedsWithoutLockingWhenAccountHasNoRows() {
        memberships.given(FIRST, OTHER, true);

        deactivate();

        verify(organizationRepository, never()).lock(any());
    }

    @Test
    @DisplayName("앞선 변경이 대기 한도 안에 끝나지 않으면 409로 실패해 다시 받을 수 있게 한다")
    void failsWhenOrganizationIsBusy() {
        memberships.given(FIRST, WITHDRAWN, true);
        doThrow(new OrganizationBusyException(new IllegalStateException("lock timeout")))
                .when(organizationRepository)
                .lock(FIRST);

        assertThatThrownBy(this::deactivate)
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrganizationErrorCode.ORGANIZATION_BUSY));
    }

    private void deactivate() {
        service.deactivate(new DeactivateWithdrawnAccountCommand(WITHDRAWN.value()));
    }
}
