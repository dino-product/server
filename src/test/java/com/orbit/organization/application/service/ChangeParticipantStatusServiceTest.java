package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.TechnicianWorkRelease;
import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.dto.ChangeParticipantStatusCommand;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.TechnicianRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.organization.domain.Technician;
import com.orbit.organization.domain.TechnicianId;
import com.orbit.organization.domain.TechnicianStatus;
import com.orbit.shared.error.BaseCode;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("참여 인원 비활성화·재활성화")
class ChangeParticipantStatusServiceTest {

    private static final Instant JOINED_AT = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-19T00:00:00Z");
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(10L);
    private static final OrganizationId OTHER_ORGANIZATION_ID = new OrganizationId(11L);
    private static final AccountId REQUESTER = new AccountId(7L);
    private static final MembershipId REQUESTER_MEMBERSHIP_ID = new MembershipId(3L);
    private static final AccountId MEMBER_ACCOUNT = new AccountId(8L);
    private static final MembershipId MEMBERSHIP_ID = new MembershipId(20L);
    private static final TechnicianId TECHNICIAN_ID = new TechnicianId(30L);

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private TechnicianRepository technicianRepository;

    @Mock
    private TechnicianWorkRelease technicianWorkRelease;

    private ChangeParticipantStatusService service;

    @BeforeEach
    void setUp() {
        service = new ChangeParticipantStatusService(
                organizationRepository,
                membershipRepository,
                technicianRepository,
                technicianWorkRelease,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("직원 소속을 발주사를 잠근 뒤 비활성화하고 참여일은 유지한다")
    void deactivatesStaffAfterLocking() {
        givenRequesterIsOwner();
        givenStaff(false, MembershipStatus.ACTIVE);
        when(membershipRepository.countActiveOwners(ORGANIZATION_ID)).thenReturn(1L);

        service.deactivate(command(ParticipationType.STAFF, MEMBERSHIP_ID.value()));

        ArgumentCaptor<Membership> saved = ArgumentCaptor.forClass(Membership.class);
        InOrder order = inOrder(organizationRepository, membershipRepository);
        order.verify(organizationRepository).lock(ORGANIZATION_ID);
        order.verify(membershipRepository).findById(MEMBERSHIP_ID);
        order.verify(membershipRepository).deactivate(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(MembershipStatus.DEACTIVATED);
        assertThat(saved.getValue().statusChangedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().joinedAt()).isEqualTo(JOINED_AT);
        verifyNoInteractions(technicianWorkRelease);
    }

    @Test
    @DisplayName("다른 총관리자가 남아 있으면 총관리자 소속도 비활성화하고 총관리자 표시를 뗀다")
    void deactivatesOwnerWhileAnotherOwnerRemains() {
        givenRequesterIsOwner();
        givenStaff(true, MembershipStatus.ACTIVE);
        when(membershipRepository.countActiveOwners(ORGANIZATION_ID)).thenReturn(2L);

        service.deactivate(command(ParticipationType.STAFF, MEMBERSHIP_ID.value()));

        ArgumentCaptor<Membership> saved = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).deactivate(saved.capture());
        assertThat(saved.getValue().isOwner()).isFalse();
        assertThat(saved.getValue().status()).isEqualTo(MembershipStatus.DEACTIVATED);
    }

    @Test
    @DisplayName("다른 총관리자가 남아 있으면 요청자는 자기 총관리자 소속도 비활성화할 수 있다")
    void deactivatesOwnMembershipWhileAnotherOwnerRemains() {
        givenRequesterIsOwner();
        when(membershipRepository.findById(REQUESTER_MEMBERSHIP_ID))
                .thenReturn(Optional.of(Membership.reconstitute(
                        REQUESTER_MEMBERSHIP_ID,
                        ORGANIZATION_ID,
                        REQUESTER,
                        true,
                        MembershipStatus.ACTIVE,
                        JOINED_AT,
                        JOINED_AT)));
        when(membershipRepository.countActiveOwners(ORGANIZATION_ID)).thenReturn(2L);

        service.deactivate(command(ParticipationType.STAFF, REQUESTER_MEMBERSHIP_ID.value()));

        ArgumentCaptor<Membership> saved = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).deactivate(saved.capture());
        assertThat(saved.getValue().id()).contains(REQUESTER_MEMBERSHIP_ID);
        assertThat(saved.getValue().isOwner()).isFalse();
        assertThat(saved.getValue().status()).isEqualTo(MembershipStatus.DEACTIVATED);
    }

    @Test
    @DisplayName("유일한 활성 총관리자는 비활성화할 수 없다")
    void rejectsDeactivatingLastOwner() {
        givenRequesterIsOwner();
        givenStaff(true, MembershipStatus.ACTIVE);
        when(membershipRepository.countActiveOwners(ORGANIZATION_ID)).thenReturn(1L);

        assertRejected(
                () -> service.deactivate(command(ParticipationType.STAFF, MEMBERSHIP_ID.value())),
                OrganizationErrorCode.LAST_OWNER);
        verify(membershipRepository, never()).deactivate(any());
    }

    @Test
    @DisplayName("기사 계약을 비활성화하면 같은 트랜잭션에서 요청자를 처리자로 배정 작업을 대기함으로 돌린다")
    void deactivatesTechnicianAndReleasesWork() {
        givenRequesterIsOwner();
        givenTechnician(TechnicianStatus.ACTIVE);

        service.deactivate(command(ParticipationType.TECHNICIAN, TECHNICIAN_ID.value()));

        ArgumentCaptor<Technician> saved = ArgumentCaptor.forClass(Technician.class);
        InOrder order = inOrder(organizationRepository, technicianRepository, technicianWorkRelease);
        order.verify(organizationRepository).lock(ORGANIZATION_ID);
        order.verify(technicianRepository).findById(TECHNICIAN_ID);
        order.verify(technicianWorkRelease)
                .releaseForDeactivation(
                        ORGANIZATION_ID.value(), TECHNICIAN_ID.value(), REQUESTER_MEMBERSHIP_ID.value());
        order.verify(technicianRepository).deactivate(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(TechnicianStatus.DEACTIVATED);
        assertThat(saved.getValue().statusChangedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("대기함 반환이 실패하면 그 예외를 그대로 전파하고 기사 계약을 저장하지 않는다")
    void propagatesWorkReleaseFailure() {
        givenRequesterIsOwner();
        givenTechnician(TechnicianStatus.ACTIVE);
        BusinessException busy = new BusinessException(CommonErrorCode.BAD_REQUEST);
        doThrow(busy).when(technicianWorkRelease).releaseForDeactivation(anyLong(), anyLong(), anyLong());

        assertThatThrownBy(() -> service.deactivate(command(ParticipationType.TECHNICIAN, TECHNICIAN_ID.value())))
                .isSameAs(busy);
        verify(technicianRepository, never()).deactivate(any());
    }

    @Test
    @DisplayName("이미 비활성이면 저장하지 않고 대기함 반환도 하지 않은 채 성공한다")
    void deactivatingAgainSucceedsWithoutChanges() {
        givenRequesterIsOwner();
        givenStaff(false, MembershipStatus.DEACTIVATED);
        givenTechnician(TechnicianStatus.DEACTIVATED);

        service.deactivate(command(ParticipationType.STAFF, MEMBERSHIP_ID.value()));
        service.deactivate(command(ParticipationType.TECHNICIAN, TECHNICIAN_ID.value()));

        verify(membershipRepository, never()).deactivate(any());
        verify(technicianRepository, never()).deactivate(any());
        verifyNoInteractions(technicianWorkRelease);
    }

    @Test
    @DisplayName("비활성 직원 소속·기사 계약을 다시 활성으로 바꾸고 세부 유형은 유지한다")
    void reactivatesStaffAndTechnician() {
        givenRequesterIsOwner();
        givenStaff(false, MembershipStatus.DEACTIVATED);
        givenTechnician(TechnicianStatus.DEACTIVATED);
        when(technicianRepository.find(ORGANIZATION_ID, MEMBER_ACCOUNT)).thenReturn(Optional.empty());
        when(membershipRepository.find(ORGANIZATION_ID, MEMBER_ACCOUNT)).thenReturn(Optional.empty());

        service.reactivate(command(ParticipationType.STAFF, MEMBERSHIP_ID.value()));
        service.reactivate(command(ParticipationType.TECHNICIAN, TECHNICIAN_ID.value()));

        ArgumentCaptor<Membership> membership = ArgumentCaptor.forClass(Membership.class);
        ArgumentCaptor<Technician> technician = ArgumentCaptor.forClass(Technician.class);
        verify(membershipRepository).reactivate(membership.capture(), eq(false));
        verify(technicianRepository).reactivate(technician.capture(), eq(false));
        assertThat(membership.getValue().status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(membership.getValue().statusChangedAt()).isEqualTo(NOW);
        assertThat(technician.getValue().status()).isEqualTo(TechnicianStatus.ACTIVE);
        assertThat(technician.getValue().id()).contains(TECHNICIAN_ID);
    }

    @Test
    @DisplayName("같은 계정이 다른 역할로 활성이면 재활성화할 수 없다")
    void rejectsReactivationWhileOtherRoleIsActive() {
        givenRequesterIsOwner();
        givenStaff(false, MembershipStatus.DEACTIVATED);
        givenTechnician(TechnicianStatus.DEACTIVATED);
        when(technicianRepository.find(ORGANIZATION_ID, MEMBER_ACCOUNT))
                .thenReturn(Optional.of(technician(TechnicianStatus.ACTIVE)));
        when(membershipRepository.find(ORGANIZATION_ID, MEMBER_ACCOUNT))
                .thenReturn(Optional.of(staff(false, MembershipStatus.ACTIVE)));

        assertRejected(
                () -> service.reactivate(command(ParticipationType.STAFF, MEMBERSHIP_ID.value())),
                OrganizationErrorCode.ACTIVE_IN_OTHER_ROLE);
        assertRejected(
                () -> service.reactivate(command(ParticipationType.TECHNICIAN, TECHNICIAN_ID.value())),
                OrganizationErrorCode.ACTIVE_IN_OTHER_ROLE);
        verify(membershipRepository, never()).reactivate(any(), anyBoolean());
        verify(technicianRepository, never()).reactivate(any(), anyBoolean());
    }

    @Test
    @DisplayName("다른 역할 행이 더 늦게 비활성이 됐으면 목록에 보이지 않는 이전 역할 행은 재활성화할 수 없다")
    void rejectsReactivatingRoleBeforeLastRoleChange() {
        givenRequesterIsOwner();
        givenStaff(false, MembershipStatus.DEACTIVATED);
        Technician deactivatedLater = Technician.reconstitute(
                TECHNICIAN_ID,
                ORGANIZATION_ID,
                MEMBER_ACCOUNT,
                TechnicianStatus.DEACTIVATED,
                JOINED_AT,
                JOINED_AT.plusSeconds(60));
        when(technicianRepository.findById(TECHNICIAN_ID)).thenReturn(Optional.of(deactivatedLater));
        when(technicianRepository.find(ORGANIZATION_ID, MEMBER_ACCOUNT)).thenReturn(Optional.of(deactivatedLater));
        when(membershipRepository.find(ORGANIZATION_ID, MEMBER_ACCOUNT))
                .thenReturn(Optional.of(staff(false, MembershipStatus.DEACTIVATED)));

        assertRejected(
                () -> service.reactivate(command(ParticipationType.STAFF, MEMBERSHIP_ID.value())),
                OrganizationErrorCode.NOT_LAST_ROLE);
        service.reactivate(command(ParticipationType.TECHNICIAN, TECHNICIAN_ID.value()));

        verify(membershipRepository, never()).reactivate(any(), anyBoolean());
        verify(technicianRepository).reactivate(any(), eq(false));
    }

    @Test
    @DisplayName("직원 소속과 기사 계약이 같은 시각에 비활성이 됐으면 직원 소속이 마지막 역할이라 기사 계약은 재활성화할 수 없다")
    void treatsStaffAsLastRoleOnSameStatusChange() {
        givenRequesterIsOwner();
        givenTechnician(TechnicianStatus.DEACTIVATED);
        when(membershipRepository.find(ORGANIZATION_ID, MEMBER_ACCOUNT))
                .thenReturn(Optional.of(staff(false, MembershipStatus.DEACTIVATED)));

        assertRejected(
                () -> service.reactivate(command(ParticipationType.TECHNICIAN, TECHNICIAN_ID.value())),
                OrganizationErrorCode.NOT_LAST_ROLE);
        verify(technicianRepository, never()).reactivate(any(), anyBoolean());
    }

    @Test
    @DisplayName("이미 활성이면 저장하지 않고 성공한다")
    void reactivatingActiveSucceedsWithoutChanges() {
        givenRequesterIsOwner();
        givenStaff(false, MembershipStatus.ACTIVE);

        service.reactivate(command(ParticipationType.STAFF, MEMBERSHIP_ID.value()));

        verify(membershipRepository, never()).reactivate(any(), anyBoolean());
    }

    @Test
    @DisplayName("없거나 다른 발주사의 대상은 찾을 수 없다고 거부한다")
    void rejectsMissingOrForeignTarget() {
        givenRequesterIsOwner();
        when(membershipRepository.findById(MEMBERSHIP_ID))
                .thenReturn(Optional.of(Membership.reconstitute(
                        MEMBERSHIP_ID,
                        OTHER_ORGANIZATION_ID,
                        MEMBER_ACCOUNT,
                        false,
                        MembershipStatus.ACTIVE,
                        JOINED_AT,
                        JOINED_AT)));
        when(technicianRepository.findById(TECHNICIAN_ID)).thenReturn(Optional.empty());

        assertRejected(
                () -> service.deactivate(command(ParticipationType.STAFF, MEMBERSHIP_ID.value())),
                OrganizationErrorCode.MEMBERSHIP_NOT_FOUND);
        assertRejected(
                () -> service.reactivate(command(ParticipationType.TECHNICIAN, TECHNICIAN_ID.value())),
                OrganizationErrorCode.TECHNICIAN_NOT_FOUND);
        verifyNoInteractions(technicianWorkRelease);
    }

    @Test
    @DisplayName("총관리자가 아닌 직원은 잠근 뒤 거부되고, 0 이하 식별자는 권한 확인 전에 입력 오류다")
    void rejectsStaffRequesterAndInvalidIds() {
        when(membershipRepository.existsActive(ORGANIZATION_ID, REQUESTER)).thenReturn(true);
        when(membershipRepository.findActive(ORGANIZATION_ID, REQUESTER))
                .thenReturn(Optional.of(Membership.reconstitute(
                        REQUESTER_MEMBERSHIP_ID,
                        ORGANIZATION_ID,
                        REQUESTER,
                        false,
                        MembershipStatus.ACTIVE,
                        JOINED_AT,
                        JOINED_AT)));

        assertRejected(
                () -> service.deactivate(command(ParticipationType.STAFF, MEMBERSHIP_ID.value())),
                OrganizationErrorCode.OWNER_ONLY);
        assertRejected(
                () -> service.reactivate(command(ParticipationType.TECHNICIAN, 0L)), CommonErrorCode.BAD_REQUEST);
        verify(organizationRepository).lock(ORGANIZATION_ID);
        verify(membershipRepository, never()).findById(any());
        verifyNoInteractions(technicianRepository, technicianWorkRelease);
    }

    private void givenRequesterIsOwner() {
        when(membershipRepository.existsActive(ORGANIZATION_ID, REQUESTER)).thenReturn(true);
        when(membershipRepository.findActive(ORGANIZATION_ID, REQUESTER))
                .thenReturn(Optional.of(Membership.reconstitute(
                        REQUESTER_MEMBERSHIP_ID,
                        ORGANIZATION_ID,
                        REQUESTER,
                        true,
                        MembershipStatus.ACTIVE,
                        JOINED_AT,
                        JOINED_AT)));
    }

    private void givenStaff(boolean owner, MembershipStatus status) {
        when(membershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(staff(owner, status)));
    }

    private void givenTechnician(TechnicianStatus status) {
        when(technicianRepository.findById(TECHNICIAN_ID)).thenReturn(Optional.of(technician(status)));
    }

    private static Membership staff(boolean owner, MembershipStatus status) {
        return Membership.reconstitute(
                MEMBERSHIP_ID, ORGANIZATION_ID, MEMBER_ACCOUNT, owner, status, JOINED_AT, JOINED_AT);
    }

    private static Technician technician(TechnicianStatus status) {
        return Technician.reconstitute(TECHNICIAN_ID, ORGANIZATION_ID, MEMBER_ACCOUNT, status, JOINED_AT, JOINED_AT);
    }

    private static void assertRejected(Runnable action, BaseCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(errorCode));
    }

    private static ChangeParticipantStatusCommand command(ParticipationType type, Long memberId) {
        return new ChangeParticipantStatusCommand(REQUESTER.value(), ORGANIZATION_ID.value(), type, memberId);
    }
}
