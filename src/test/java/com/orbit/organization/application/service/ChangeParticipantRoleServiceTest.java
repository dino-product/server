package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
import com.orbit.organization.TechnicianWorkReleaseBusyException;
import com.orbit.organization.WorkReleaseBlocked;
import com.orbit.organization.WorkReleased;
import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.dto.ChangeParticipantRoleCommand;
import com.orbit.organization.application.port.in.command.dto.ParticipantRoleChangeInfo;
import com.orbit.organization.application.port.out.MemberCategoryRepository;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.TechnicianRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.CategoryColor;
import com.orbit.organization.domain.CategoryName;
import com.orbit.organization.domain.MemberCategory;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.MemberCategoryKind;
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
@DisplayName("참여 인원 역할 변경")
class ChangeParticipantRoleServiceTest {

    private static final Instant JOINED_AT = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant CHANGED_AT = Instant.parse("2026-10-05T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(10L);
    private static final OrganizationId OTHER_ORGANIZATION_ID = new OrganizationId(11L);
    private static final AccountId REQUESTER = new AccountId(7L);
    private static final MembershipId REQUESTER_MEMBERSHIP_ID = new MembershipId(3L);
    private static final AccountId MEMBER_ACCOUNT = new AccountId(8L);
    private static final MembershipId MEMBERSHIP_ID = new MembershipId(20L);
    private static final TechnicianId TECHNICIAN_ID = new TechnicianId(30L);
    private static final MemberCategoryId CATEGORY_ID = new MemberCategoryId(40L);

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private TechnicianRepository technicianRepository;

    @Mock
    private MemberCategoryRepository categoryRepository;

    @Mock
    private TechnicianWorkRelease technicianWorkRelease;

    private ChangeParticipantRoleService service;

    @BeforeEach
    void setUp() {
        service = new ChangeParticipantRoleService(
                organizationRepository,
                membershipRepository,
                technicianRepository,
                categoryRepository,
                technicianWorkRelease,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("기사를 직원으로 바꾸면 예정 작업을 대기함으로 돌린 뒤 기사 계약을 끝내고 이전 참여일로 직원 소속을 만든다")
    void changesTechnicianToNewStaffAfterReleasingWorks() {
        givenRequesterIsOwner();
        givenTechnician(TechnicianStatus.ACTIVE);
        givenCategory(MemberCategoryKind.STAFF, ORGANIZATION_ID, false);
        when(membershipRepository.find(ORGANIZATION_ID, MEMBER_ACCOUNT)).thenReturn(Optional.empty());
        when(technicianWorkRelease.releaseForRoleChange(
                        ORGANIZATION_ID.value(), TECHNICIAN_ID.value(), REQUESTER_MEMBERSHIP_ID.value()))
                .thenReturn(new WorkReleased(List.of(100L, 101L)));
        when(membershipRepository.save(any()))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), MEMBERSHIP_ID));

        ParticipantRoleChangeInfo info = service.changeRole(command(ParticipationType.TECHNICIAN, TECHNICIAN_ID));

        assertThat(info)
                .isEqualTo(new ParticipantRoleChangeInfo(
                        ParticipationType.STAFF, MEMBERSHIP_ID.value(), List.of(100L, 101L)));
        ArgumentCaptor<Technician> ended = ArgumentCaptor.forClass(Technician.class);
        ArgumentCaptor<Membership> created = ArgumentCaptor.forClass(Membership.class);
        InOrder order =
                inOrder(organizationRepository, technicianRepository, technicianWorkRelease, membershipRepository);
        order.verify(organizationRepository).lock(ORGANIZATION_ID);
        order.verify(technicianRepository).findById(TECHNICIAN_ID);
        order.verify(technicianWorkRelease)
                .releaseForRoleChange(ORGANIZATION_ID.value(), TECHNICIAN_ID.value(), REQUESTER_MEMBERSHIP_ID.value());
        order.verify(technicianRepository).deactivate(ended.capture());
        order.verify(membershipRepository).save(created.capture());
        order.verify(membershipRepository).changeStaffCategory(MEMBERSHIP_ID, CATEGORY_ID);
        assertThat(ended.getValue().status()).isEqualTo(TechnicianStatus.DEACTIVATED);
        assertThat(ended.getValue().statusChangedAt()).isEqualTo(NOW);
        assertThat(created.getValue().status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(created.getValue().isOwner()).isFalse();
        assertThat(created.getValue().joinedAt()).isEqualTo(JOINED_AT);
        assertThat(created.getValue().statusChangedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("기사를 직원으로 바꿀 때 이전 직원 소속이 있으면 그 행을 되살리고 예전 직원 유형은 해제한다")
    void reactivatesPreviousStaffMembership() {
        givenRequesterIsOwner();
        givenTechnician(TechnicianStatus.ACTIVE);
        Membership previous = Membership.reconstitute(
                MEMBERSHIP_ID,
                ORGANIZATION_ID,
                MEMBER_ACCOUNT,
                false,
                MembershipStatus.DEACTIVATED,
                JOINED_AT,
                CHANGED_AT);
        when(membershipRepository.find(ORGANIZATION_ID, MEMBER_ACCOUNT)).thenReturn(Optional.of(previous));
        when(technicianWorkRelease.releaseForRoleChange(anyLong(), anyLong(), anyLong()))
                .thenReturn(new WorkReleased(List.of()));
        when(membershipRepository.reactivate(any(), anyBoolean())).thenAnswer(invocation -> invocation.getArgument(0));

        ParticipantRoleChangeInfo info =
                service.changeRole(command(ParticipationType.TECHNICIAN, TECHNICIAN_ID.value(), null));

        assertThat(info)
                .isEqualTo(new ParticipantRoleChangeInfo(ParticipationType.STAFF, MEMBERSHIP_ID.value(), List.of()));
        ArgumentCaptor<Membership> reactivated = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).reactivate(reactivated.capture(), org.mockito.ArgumentMatchers.eq(true));
        assertThat(reactivated.getValue().status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(reactivated.getValue().joinedAt()).isEqualTo(JOINED_AT);
        assertThat(reactivated.getValue().statusChangedAt()).isEqualTo(NOW);
        verify(membershipRepository, never()).save(any());
        verify(membershipRepository, never()).changeStaffCategory(any(), any());
        verify(technicianRepository).deactivate(any());
        verifyNoInteractions(categoryRepository);
    }

    @Test
    @DisplayName("작업중 작업이 있는 기사는 409 ORGANIZATION-027로 거부하고 아무것도 저장하지 않는다")
    void rejectsTechnicianWithWorkInProgress() {
        givenRequesterIsOwner();
        givenTechnician(TechnicianStatus.ACTIVE);
        when(technicianWorkRelease.releaseForRoleChange(anyLong(), anyLong(), anyLong()))
                .thenReturn(new WorkReleaseBlocked(List.of(5L)));

        assertRejected(
                () -> service.changeRole(command(ParticipationType.TECHNICIAN, TECHNICIAN_ID.value(), null)),
                OrganizationErrorCode.TECHNICIAN_WORK_IN_PROGRESS);
        verify(technicianRepository, never()).deactivate(any());
        verify(membershipRepository, never()).save(any());
        verify(membershipRepository, never()).reactivate(any(), anyBoolean());
    }

    @Test
    @DisplayName("기사 일정이 바빠 대기함 반환이 대기 한도를 넘기면 409 ORGANIZATION-024로 바꾸고 역할을 바꾸지 않는다")
    void translatesBusyWorkRelease() {
        givenRequesterIsOwner();
        givenTechnician(TechnicianStatus.ACTIVE);
        TechnicianWorkReleaseBusyException busy = new TechnicianWorkReleaseBusyException(null);
        doThrow(busy).when(technicianWorkRelease).releaseForRoleChange(anyLong(), anyLong(), anyLong());

        assertThatThrownBy(() -> service.changeRole(command(ParticipationType.TECHNICIAN, TECHNICIAN_ID.value(), null)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(OrganizationErrorCode.TECHNICIAN_SCHEDULE_BUSY);
                    assertThat(exception.getCause()).isSameAs(busy);
                });
        verify(technicianRepository, never()).deactivate(any());
    }

    @Test
    @DisplayName("대기함 반환의 그 밖의 실패는 그대로 전파하고 역할을 바꾸지 않는다")
    void propagatesWorkReleaseFailure() {
        givenRequesterIsOwner();
        givenTechnician(TechnicianStatus.ACTIVE);
        IllegalStateException failure = new IllegalStateException("broken");
        doThrow(failure).when(technicianWorkRelease).releaseForRoleChange(anyLong(), anyLong(), anyLong());

        assertThatThrownBy(() -> service.changeRole(command(ParticipationType.TECHNICIAN, TECHNICIAN_ID.value(), null)))
                .isSameAs(failure);
        verify(technicianRepository, never()).deactivate(any());
    }

    @Test
    @DisplayName("비활성 기사 계약은 409 ORGANIZATION-026으로 거부하고 대기함 반환을 부르지 않는다")
    void rejectsInactiveTechnician() {
        givenRequesterIsOwner();
        givenTechnician(TechnicianStatus.DEACTIVATED);

        assertRejected(
                () -> service.changeRole(command(ParticipationType.TECHNICIAN, TECHNICIAN_ID.value(), null)),
                OrganizationErrorCode.INACTIVE_MEMBER_ROLE);
        verifyNoInteractions(technicianWorkRelease);
    }

    @Test
    @DisplayName("다른 발주사의 기사 계약은 404 ORGANIZATION-020으로 거부한다")
    void rejectsTechnicianOfOtherOrganization() {
        givenRequesterIsOwner();
        when(technicianRepository.findById(TECHNICIAN_ID))
                .thenReturn(Optional.of(Technician.reconstitute(
                        TECHNICIAN_ID,
                        OTHER_ORGANIZATION_ID,
                        MEMBER_ACCOUNT,
                        TechnicianStatus.ACTIVE,
                        JOINED_AT,
                        JOINED_AT)));

        assertRejected(
                () -> service.changeRole(command(ParticipationType.TECHNICIAN, TECHNICIAN_ID.value(), null)),
                OrganizationErrorCode.TECHNICIAN_NOT_FOUND);
        verifyNoInteractions(technicianWorkRelease);
    }

    @Test
    @DisplayName("새 역할의 유형이 삭제됐거나 다른 종류·다른 발주사의 유형이면 404 ORGANIZATION-008로 거부하고 대기함 반환을 부르지 않는다")
    void rejectsUnavailableCategory() {
        givenRequesterIsOwner();
        givenTechnician(TechnicianStatus.ACTIVE);
        givenCategory(MemberCategoryKind.STAFF, ORGANIZATION_ID, true);

        assertRejected(
                () -> service.changeRole(command(ParticipationType.TECHNICIAN, TECHNICIAN_ID)),
                OrganizationErrorCode.CATEGORY_NOT_FOUND);
        verifyNoInteractions(technicianWorkRelease);
    }

    @Test
    @DisplayName("총관리자가 아닌 요청자는 403 ORGANIZATION-003으로 거부한다")
    void rejectsNonOwnerRequester() {
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
                () -> service.changeRole(command(ParticipationType.TECHNICIAN, TECHNICIAN_ID.value(), null)),
                OrganizationErrorCode.OWNER_ONLY);
        verifyNoInteractions(technicianWorkRelease);
    }

    @Test
    @DisplayName("0 이하 식별자는 권한보다 먼저 400 COMMON-400으로 거부한다")
    void rejectsNonPositiveIds() {
        assertRejected(
                () -> service.changeRole(command(ParticipationType.TECHNICIAN, 0L, null)), CommonErrorCode.BAD_REQUEST);
        assertRejected(
                () -> service.changeRole(command(ParticipationType.TECHNICIAN, TECHNICIAN_ID.value(), 0L)),
                CommonErrorCode.BAD_REQUEST);
        verifyNoInteractions(organizationRepository, technicianWorkRelease);
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

    private void givenTechnician(TechnicianStatus status) {
        when(technicianRepository.findById(TECHNICIAN_ID))
                .thenReturn(Optional.of(Technician.reconstitute(
                        TECHNICIAN_ID, ORGANIZATION_ID, MEMBER_ACCOUNT, status, JOINED_AT, JOINED_AT)));
    }

    private void givenCategory(MemberCategoryKind kind, OrganizationId organizationId, boolean deleted) {
        MemberCategory category = MemberCategory.reconstitute(
                CATEGORY_ID,
                organizationId,
                kind,
                new CategoryName("설비"),
                new CategoryColor("#3B82F6"),
                deleted,
                deleted ? JOINED_AT : null);
        when(categoryRepository.findById(kind, CATEGORY_ID)).thenReturn(Optional.of(category));
    }

    private static Membership withId(Membership membership, MembershipId id) {
        return Membership.reconstitute(
                id,
                membership.organizationId(),
                membership.accountId(),
                membership.isOwner(),
                membership.status(),
                membership.joinedAt(),
                membership.statusChangedAt());
    }

    private static void assertRejected(Runnable action, BaseCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(errorCode));
    }

    private static ChangeParticipantRoleCommand command(ParticipationType type, TechnicianId technicianId) {
        return command(type, technicianId.value(), CATEGORY_ID.value());
    }

    private static ChangeParticipantRoleCommand command(ParticipationType type, Long memberId, Long categoryId) {
        return new ChangeParticipantRoleCommand(REQUESTER.value(), ORGANIZATION_ID.value(), type, memberId, categoryId);
    }
}
