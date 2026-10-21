package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.query.dto.GetWithdrawalBlockingOwnershipsQuery;
import com.orbit.organization.application.port.out.OrganizationBusyException;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.shared.error.BusinessException;

@DisplayName("탈퇴를 막는 유일한 총관리자 판정")
class GetWithdrawalBlockingOwnershipsServiceTest {

    private static final AccountId WITHDRAWING = new AccountId(7L);
    private static final AccountId OTHER = new AccountId(8L);
    private static final OrganizationId FIRST = new OrganizationId(1L);
    private static final OrganizationId SECOND = new OrganizationId(2L);
    private static final OrganizationId THIRD = new OrganizationId(3L);

    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();

    private GetWithdrawalBlockingOwnershipsService service;

    @BeforeEach
    void setUp() {
        service = new GetWithdrawalBlockingOwnershipsService(organizationRepository, memberships);
    }

    @Test
    @DisplayName("다른 활성 총관리자가 없는 발주사만 세고, 발주사를 식별자 오름차순으로 잠근 뒤 센다")
    void countsOrganizationsWhereAccountIsSoleOwner() {
        memberships.given(THIRD, WITHDRAWING, true);
        memberships.given(FIRST, WITHDRAWING, true);
        memberships.given(SECOND, WITHDRAWING, true);
        memberships.given(SECOND, OTHER, true);
        memberships.given(FIRST, OTHER, false);

        assertThat(count()).isEqualTo(2);
        InOrder locks = inOrder(organizationRepository);
        locks.verify(organizationRepository).lock(FIRST);
        locks.verify(organizationRepository).lock(SECOND);
        locks.verify(organizationRepository).lock(THIRD);
    }

    @Test
    @DisplayName("다른 활성 직원·기사가 있어도 다른 총관리자가 없으면 유일한 총관리자다")
    void staffDoNotReplaceOwner() {
        memberships.given(FIRST, WITHDRAWING, true);
        memberships.given(FIRST, OTHER, false);

        assertThat(count()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 총관리자가 비활성이면 남은 총관리자로 세지 않는다")
    void deactivatedOtherOwnerDoesNotCount() {
        memberships.given(FIRST, WITHDRAWING, true);
        memberships.given(FIRST, OTHER, true, MembershipStatus.DEACTIVATED);

        assertThat(count()).isEqualTo(1);
    }

    @Test
    @DisplayName("총관리자인 발주사가 없으면 발주사를 잠그지 않고 0이다")
    void returnsZeroWithoutLockingWhenNotOwner() {
        memberships.given(FIRST, WITHDRAWING, false);
        memberships.given(SECOND, WITHDRAWING, true, MembershipStatus.DEACTIVATED);

        assertThat(count()).isZero();
        verify(organizationRepository, never()).lock(any());
    }

    @Test
    @DisplayName("잠금을 기다리는 사이 총관리자에서 해제됐으면 그 발주사는 세지 않는다")
    void skipsOrganizationWhereOwnershipWasRevokedWhileWaiting() {
        Membership owner = memberships.given(FIRST, WITHDRAWING, true);
        memberships.given(FIRST, OTHER, true);
        doAnswer(invocation -> {
                    memberships.save(owner.revokeOwner(2));
                    memberships.deactivate(
                            memberships.find(FIRST, OTHER).orElseThrow().id().orElseThrow());
                    return null;
                })
                .when(organizationRepository)
                .lock(FIRST);

        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("판정은 소속을 바꾸지 않는다")
    void doesNotChangeMemberships() {
        memberships.given(FIRST, WITHDRAWING, true);

        count();

        assertThat(memberships.saveCount()).isZero();
        assertThat(memberships.find(FIRST, WITHDRAWING).orElseThrow().status()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    @DisplayName("앞선 변경이 대기 한도 안에 끝나지 않으면 409로 거부한다")
    void rejectsWhenOrganizationIsBusy() {
        memberships.given(FIRST, WITHDRAWING, true);
        doThrow(new OrganizationBusyException(new IllegalStateException("lock timeout")))
                .when(organizationRepository)
                .lock(FIRST);

        assertThatThrownBy(this::count)
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrganizationErrorCode.ORGANIZATION_BUSY));
    }

    private int count() {
        return service.get(new GetWithdrawalBlockingOwnershipsQuery(WITHDRAWING.value()))
                .soleOwnerOrganizationCount();
    }
}
