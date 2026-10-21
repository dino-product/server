package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.orbit.organization.ParticipationRequestApproved;
import com.orbit.organization.ParticipationRequestRejected;
import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.dto.ApproveParticipationRequestsCommand;
import com.orbit.organization.application.port.in.command.dto.ApproveParticipationRequestsCommand.Approval;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestDecisionInfo;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestDecisionOutcome;
import com.orbit.organization.application.port.in.command.dto.RejectParticipationRequestsCommand;
import com.orbit.organization.application.port.out.OrganizationBusyException;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.ParticipationRequestRepository;
import com.orbit.organization.application.port.out.TechnicianRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationRequest;
import com.orbit.organization.domain.ParticipationRequestId;
import com.orbit.organization.domain.ParticipationRequestStatus;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.organization.domain.RequestChannel;
import com.orbit.organization.domain.Technician;
import com.orbit.organization.domain.TechnicianId;
import com.orbit.organization.domain.TechnicianStatus;
import com.orbit.shared.error.BaseCode;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("참여 요청 승인·거절")
class DecideParticipationRequestsServiceTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-10-06T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(10L);
    private static final OrganizationId OTHER_ORGANIZATION_ID = new OrganizationId(11L);
    private static final AccountId OWNER = new AccountId(1L);
    private static final AccountId STAFF = new AccountId(2L);

    @Mock
    private ParticipationRequestRepository requestRepository;

    @Mock
    private TechnicianRepository technicianRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private InMemoryMembershipRepository membershipRepository;
    private Membership ownerMembership;
    private final Map<Long, ParticipationRequest> requests = new HashMap<>();
    private DecideParticipationRequestsService service;

    @BeforeEach
    void setUp() {
        membershipRepository = new InMemoryMembershipRepository();
        ownerMembership = membershipRepository.given(ORGANIZATION_ID, OWNER, true);
        membershipRepository.given(ORGANIZATION_ID, STAFF, false);
        service = new DecideParticipationRequestsService(
                membershipRepository,
                requestRepository,
                technicianRepository,
                organizationRepository,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient()
                .when(requestRepository.findByIdForUpdate(any()))
                .thenAnswer(invocation -> Optional.ofNullable(requests.get(
                        invocation.<ParticipationRequestId>getArgument(0).value())));
        lenient().when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(technicianRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(technicianRepository.find(any(), any())).thenReturn(Optional.empty());
        lenient()
                .when(technicianRepository.reactivate(any(), anyBoolean()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("희망 유형 그대로 기사로 승인하면 처리자를 남기고 활성 기사 계약을 만든다")
    void approvesAsDesiredTechnician() {
        givenPending(31L, 7L, ParticipationType.TECHNICIAN);

        List<ParticipationRequestDecisionInfo> results =
                service.approve(approve(OWNER, new Approval(31L, ParticipationType.TECHNICIAN)));

        assertThat(results)
                .containsExactly(new ParticipationRequestDecisionInfo(
                        31L, ParticipationRequestDecisionOutcome.APPROVED, ParticipationRequestStatus.APPROVED));
        ParticipationRequest approved = savedRequest();
        assertThat(approved.confirmedType()).contains(ParticipationType.TECHNICIAN);
        assertThat(approved.decidedBy()).isEqualTo(ownerMembership.id());
        assertThat(approved.decidedAt()).contains(NOW);
        ArgumentCaptor<Technician> technician = ArgumentCaptor.forClass(Technician.class);
        verify(technicianRepository).save(technician.capture());
        assertThat(technician.getValue())
                .extracting(
                        Technician::organizationId,
                        Technician::accountId,
                        Technician::status,
                        Technician::statusChangedAt)
                .containsExactly(ORGANIZATION_ID, new AccountId(7L), TechnicianStatus.ACTIVE, NOW);
        assertThat(membershipRepository.saveCount()).isZero();
    }

    @Test
    @DisplayName("승인한 요청마다 희망·확정 유형을 담은 승인 이벤트를 발행하고, 처리하지 못한 행은 발행하지 않는다")
    void publishesApprovedEventOnlyForApprovedRows() {
        givenPending(31L, 7L, ParticipationType.TECHNICIAN);
        givenDecided(32L, 8L, ParticipationRequestStatus.CANCELED);

        service.approve(
                approve(OWNER, new Approval(31L, ParticipationType.STAFF), new Approval(32L, ParticipationType.STAFF)));

        verify(eventPublisher)
                .publishEvent(new ParticipationRequestApproved(
                        31L,
                        10L,
                        7L,
                        ParticipationRequestApproved.Type.TECHNICIAN,
                        ParticipationRequestApproved.Type.STAFF,
                        NOW));
        verifyNoMoreInteractions(eventPublisher);
    }

    @Test
    @DisplayName("거절한 요청마다 사유를 담은 거절 이벤트를 발행한다")
    void publishesRejectedEventWithReason() {
        givenPending(31L, 7L, ParticipationType.STAFF);
        givenPending(32L, 8L, ParticipationType.STAFF);

        service.reject(new RejectParticipationRequestsCommand(OWNER.value(), 10L, List.of(32L, 31L), " 인원 마감 "));

        InOrder order = inOrder(eventPublisher);
        order.verify(eventPublisher).publishEvent(new ParticipationRequestRejected(31L, 10L, 7L, "인원 마감", NOW));
        order.verify(eventPublisher).publishEvent(new ParticipationRequestRejected(32L, 10L, 8L, "인원 마감", NOW));
    }

    @Test
    @DisplayName("신청자에게 이미 활성 소속이 있어 승인하지 못하면 이벤트를 발행하지 않는다")
    void publishesNothingWhenApprovalIsSkipped() {
        givenPending(31L, 7L, ParticipationType.STAFF);
        givenTechnician(7L, TechnicianStatus.ACTIVE, REQUESTED_AT);

        service.approve(approve(OWNER, new Approval(31L, ParticipationType.STAFF)));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("기사 희망 요청을 직원으로 바꿔 승인하면 총관리자 표시 없는 활성 직원 소속을 만든다")
    void approvesTechnicianRequestAsStaff() {
        givenPending(31L, 7L, ParticipationType.TECHNICIAN);

        service.approve(approve(OWNER, new Approval(31L, ParticipationType.STAFF)));

        assertThat(savedRequest().confirmedType()).contains(ParticipationType.STAFF);
        assertThat(membershipRepository.findActive(ORGANIZATION_ID, new AccountId(7L)))
                .hasValueSatisfying(membership -> {
                    assertThat(membership.isOwner()).isFalse();
                    assertThat(membership.joinedAt()).isEqualTo(NOW);
                });
        verify(technicianRepository, never()).save(any());
    }

    @Test
    @DisplayName("확정 유형이 서로 다른 요청을 한 번에 승인하면 각 행의 확정 유형으로 처리한다")
    void approvesEachRowWithItsOwnConfirmedType() {
        givenPending(31L, 7L, ParticipationType.STAFF);
        givenPending(32L, 8L, ParticipationType.STAFF);

        List<ParticipationRequestDecisionInfo> results = service.approve(approve(
                OWNER, new Approval(31L, ParticipationType.STAFF), new Approval(32L, ParticipationType.TECHNICIAN)));

        assertThat(results)
                .extracting(ParticipationRequestDecisionInfo::requestId, ParticipationRequestDecisionInfo::outcome)
                .containsExactly(
                        tuple(31L, ParticipationRequestDecisionOutcome.APPROVED),
                        tuple(32L, ParticipationRequestDecisionOutcome.APPROVED));
        assertThat(membershipRepository.findActive(ORGANIZATION_ID, new AccountId(7L)))
                .isPresent();
        assertThat(membershipRepository.findActive(ORGANIZATION_ID, new AccountId(8L)))
                .isEmpty();
        verify(technicianRepository).save(any(Technician.class));
    }

    @Test
    @DisplayName("요청 행은 입력 순서와 관계없이 식별자 오름차순으로 잠그고, 결과는 입력 순서대로 돌려준다")
    void locksRequestsInAscendingIdOrder() {
        givenPending(31L, 7L, ParticipationType.STAFF);
        givenPending(32L, 8L, ParticipationType.STAFF);

        List<ParticipationRequestDecisionInfo> results = service.approve(
                approve(OWNER, new Approval(32L, ParticipationType.STAFF), new Approval(31L, ParticipationType.STAFF)));

        InOrder order = inOrder(requestRepository);
        order.verify(requestRepository).findByIdForUpdate(new ParticipationRequestId(31L));
        order.verify(requestRepository).findByIdForUpdate(new ParticipationRequestId(32L));
        assertThat(results)
                .extracting(ParticipationRequestDecisionInfo::requestId)
                .containsExactly(32L, 31L);
    }

    @Test
    @DisplayName("이미 처리되거나 취소된 요청은 다시 처리하지 않고 현재 상태와 함께 실패로 알리며, 나머지 행은 처리한다")
    void reportsNonPendingRequestsWithoutReprocessing() {
        givenPending(31L, 7L, ParticipationType.STAFF);
        givenDecided(32L, 8L, ParticipationRequestStatus.CANCELED);
        givenDecided(33L, 9L, ParticipationRequestStatus.REJECTED);

        List<ParticipationRequestDecisionInfo> results = service.approve(approve(
                OWNER,
                new Approval(31L, ParticipationType.STAFF),
                new Approval(32L, ParticipationType.STAFF),
                new Approval(33L, ParticipationType.STAFF)));

        assertThat(results)
                .extracting(ParticipationRequestDecisionInfo::outcome, ParticipationRequestDecisionInfo::status)
                .containsExactly(
                        tuple(ParticipationRequestDecisionOutcome.APPROVED, ParticipationRequestStatus.APPROVED),
                        tuple(ParticipationRequestDecisionOutcome.NOT_PENDING, ParticipationRequestStatus.CANCELED),
                        tuple(ParticipationRequestDecisionOutcome.NOT_PENDING, ParticipationRequestStatus.REJECTED));
        verify(requestRepository).save(any());
        assertThat(membershipRepository.findActive(ORGANIZATION_ID, new AccountId(8L)))
                .isEmpty();
    }

    @Test
    @DisplayName("없는 요청과 다른 발주사의 요청은 구분하지 않고 찾을 수 없다고 알린다")
    void hidesMissingAndOtherOrganizationRequests() {
        requests.put(
                32L,
                ParticipationRequest.reconstitute(
                        new ParticipationRequestId(32L),
                        OTHER_ORGANIZATION_ID,
                        new AccountId(8L),
                        ParticipationType.STAFF,
                        RequestChannel.COMPANY_CODE,
                        null,
                        ParticipationRequestStatus.PENDING,
                        null,
                        REQUESTED_AT,
                        null,
                        null));

        List<ParticipationRequestDecisionInfo> results = service.approve(
                approve(OWNER, new Approval(31L, ParticipationType.STAFF), new Approval(32L, ParticipationType.STAFF)));

        assertThat(results)
                .containsExactly(
                        new ParticipationRequestDecisionInfo(31L, ParticipationRequestDecisionOutcome.NOT_FOUND, null),
                        new ParticipationRequestDecisionInfo(32L, ParticipationRequestDecisionOutcome.NOT_FOUND, null));
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("신청자에게 그 발주사의 활성 직원 소속이나 기사 계약이 있으면 그 행만 실패로 두고 요청은 승인 대기로 남긴다")
    void leavesRequestPendingWhenApplicantIsActiveMember() {
        givenPending(31L, 7L, ParticipationType.TECHNICIAN);
        givenPending(32L, 8L, ParticipationType.STAFF);
        membershipRepository.given(ORGANIZATION_ID, new AccountId(7L), false);
        givenTechnician(8L, TechnicianStatus.ACTIVE, REQUESTED_AT);

        List<ParticipationRequestDecisionInfo> results = service.approve(approve(
                OWNER, new Approval(31L, ParticipationType.TECHNICIAN), new Approval(32L, ParticipationType.STAFF)));

        assertThat(results)
                .extracting(ParticipationRequestDecisionInfo::outcome, ParticipationRequestDecisionInfo::status)
                .containsOnly(
                        tuple(ParticipationRequestDecisionOutcome.ALREADY_MEMBER, ParticipationRequestStatus.PENDING));
        verify(requestRepository, never()).save(any());
        verify(technicianRepository, never()).save(any());
        verify(technicianRepository, never()).reactivate(any(), anyBoolean());
        assertThat(membershipRepository.saveCount()).isZero();
    }

    @Test
    @DisplayName("다른 발주사의 소속은 승인을 막지 않는다")
    void ignoresRowsOfOtherOrganizations() {
        givenPending(31L, 7L, ParticipationType.STAFF);
        membershipRepository.given(OTHER_ORGANIZATION_ID, new AccountId(7L), true);

        assertThat(service.approve(approve(OWNER, new Approval(31L, ParticipationType.STAFF))))
                .extracting(ParticipationRequestDecisionInfo::outcome)
                .containsExactly(ParticipationRequestDecisionOutcome.APPROVED);
    }

    @Test
    @DisplayName("비활성 직원 소속이 있는 회원을 직원으로 승인하면 새 소속 없이 그 소속을 활성으로 되돌리고 참여일·세부 유형을 유지하며 총관리자 표시는 끈다")
    void reactivatesDeactivatedMembershipForSameRole() {
        givenPending(31L, 7L, ParticipationType.STAFF);
        Membership deactivated =
                membershipRepository.given(ORGANIZATION_ID, new AccountId(7L), true, MembershipStatus.DEACTIVATED);

        List<ParticipationRequestDecisionInfo> results =
                service.approve(approve(OWNER, new Approval(31L, ParticipationType.STAFF)));

        assertThat(results)
                .extracting(ParticipationRequestDecisionInfo::outcome)
                .containsExactly(ParticipationRequestDecisionOutcome.APPROVED);
        Membership reactivated = membershipRepository.stored(deactivated.id().orElseThrow());
        assertThat(reactivated.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(reactivated.isOwner()).isFalse();
        assertThat(reactivated.joinedAt()).isEqualTo(deactivated.joinedAt());
        assertThat(reactivated.statusChangedAt()).isEqualTo(NOW);
        assertThat(membershipRepository.reactivations()).containsExactly(false);
        assertThat(membershipRepository.saveCount()).isZero();
        verify(technicianRepository, never()).save(any());
        assertThat(savedRequest().status()).isEqualTo(ParticipationRequestStatus.APPROVED);
    }

    @Test
    @DisplayName("비활성 기사 계약이 있는 회원을 기사로 승인하면 같은 계약을 활성으로 되돌리고 세부 유형을 유지한다")
    void reactivatesDeactivatedTechnicianForSameRole() {
        givenPending(31L, 7L, ParticipationType.TECHNICIAN);
        Technician deactivated = givenTechnician(7L, TechnicianStatus.DEACTIVATED, REQUESTED_AT);

        service.approve(approve(OWNER, new Approval(31L, ParticipationType.TECHNICIAN)));

        ArgumentCaptor<Technician> reactivated = ArgumentCaptor.forClass(Technician.class);
        verify(technicianRepository).reactivate(reactivated.capture(), org.mockito.ArgumentMatchers.eq(false));
        assertThat(reactivated.getValue())
                .extracting(Technician::id, Technician::status, Technician::joinedAt, Technician::statusChangedAt)
                .containsExactly(deactivated.id(), TechnicianStatus.ACTIVE, deactivated.joinedAt(), NOW);
        verify(technicianRepository, never()).save(any());
    }

    @Test
    @DisplayName("비활성 직원 소속만 있는 회원을 기사로 확정하면 그 소속은 비활성으로 두고 이전 참여일을 이어받은 기사 계약을 만든다")
    void createsTechnicianKeepingJoinDateWhenRoleDiffers() {
        givenPending(31L, 7L, ParticipationType.STAFF);
        Membership deactivated =
                membershipRepository.given(ORGANIZATION_ID, new AccountId(7L), false, MembershipStatus.DEACTIVATED);

        service.approve(approve(OWNER, new Approval(31L, ParticipationType.TECHNICIAN)));

        ArgumentCaptor<Technician> created = ArgumentCaptor.forClass(Technician.class);
        verify(technicianRepository).save(created.capture());
        assertThat(created.getValue())
                .extracting(Technician::status, Technician::joinedAt, Technician::statusChangedAt)
                .containsExactly(TechnicianStatus.ACTIVE, deactivated.joinedAt(), NOW);
        assertThat(membershipRepository.stored(deactivated.id().orElseThrow()).status())
                .isEqualTo(MembershipStatus.DEACTIVATED);
        assertThat(membershipRepository.reactivations()).isEmpty();
    }

    @Test
    @DisplayName("탈퇴로 비활성화된 행이 있는 회원은 같은 역할로도 다른 역할로도 되살리거나 새로 만들지 않는다")
    void neverRejoinsWithdrawnMember() {
        givenPending(31L, 7L, ParticipationType.STAFF);
        Membership withdrawn = membershipRepository.given(ORGANIZATION_ID, new AccountId(7L), false);
        membershipRepository.withdraw(withdrawn.withdraw(REQUESTED_AT));

        assertThatThrownBy(() -> service.approve(approve(OWNER, new Approval(31L, ParticipationType.STAFF))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.approve(approve(OWNER, new Approval(31L, ParticipationType.TECHNICIAN))))
                .isInstanceOf(IllegalStateException.class);
        verify(technicianRepository, never()).save(any());
        assertThat(membershipRepository.reactivations()).isEmpty();
    }

    @Test
    @DisplayName("두 행이 모두 비활성이면 마지막 역할과 같은 확정 유형은 세부 유형을 유지하고, 다른 확정 유형은 그 행을 되돌리며 세부 유형을 해제한다")
    void unassignsCategoryWhenConfirmedTypeDiffersFromLastRole() {
        givenPending(31L, 7L, ParticipationType.STAFF);
        membershipRepository.given(ORGANIZATION_ID, new AccountId(7L), false, MembershipStatus.DEACTIVATED);
        givenTechnician(7L, TechnicianStatus.DEACTIVATED, REQUESTED_AT.plusSeconds(60));

        service.approve(approve(OWNER, new Approval(31L, ParticipationType.STAFF)));

        assertThat(membershipRepository.findActive(ORGANIZATION_ID, new AccountId(7L)))
                .isPresent();
        assertThat(membershipRepository.reactivations()).containsExactly(true);
        verify(technicianRepository, never()).reactivate(any(), anyBoolean());
        verify(technicianRepository, never()).save(any());
    }

    @Test
    @DisplayName("두 행이 모두 비활성이고 마지막 역할이 직원이면, 기사로 확정할 때 기사 계약을 되살리며 세부 유형을 해제한다")
    void unassignsTechnicianCategoryWhenLastRoleWasStaff() {
        givenPending(31L, 7L, ParticipationType.TECHNICIAN);
        membershipRepository.given(
                ORGANIZATION_ID, new AccountId(7L), false, MembershipStatus.DEACTIVATED, REQUESTED_AT);
        givenTechnician(7L, TechnicianStatus.DEACTIVATED, REQUESTED_AT.minusSeconds(60));

        service.approve(approve(OWNER, new Approval(31L, ParticipationType.TECHNICIAN)));

        verify(technicianRepository).reactivate(any(), org.mockito.ArgumentMatchers.eq(true));
        assertThat(membershipRepository.reactivations()).isEmpty();
        assertThat(membershipRepository.findActive(ORGANIZATION_ID, new AccountId(7L)))
                .isEmpty();
    }

    @Test
    @DisplayName("두 행이 모두 비활성이고 확정 유형이 마지막 역할과 같으면 그 행만 되살리며 세부 유형을 유지하고 다른 행은 비활성으로 둔다")
    void keepsCategoryWhenConfirmedTypeMatchesLastRole() {
        givenPending(31L, 7L, ParticipationType.TECHNICIAN);
        membershipRepository.given(ORGANIZATION_ID, new AccountId(7L), false, MembershipStatus.DEACTIVATED);
        givenTechnician(7L, TechnicianStatus.DEACTIVATED, REQUESTED_AT.plusSeconds(60));

        service.approve(approve(OWNER, new Approval(31L, ParticipationType.TECHNICIAN)));

        verify(technicianRepository).reactivate(any(), org.mockito.ArgumentMatchers.eq(false));
        assertThat(membershipRepository.reactivations()).isEmpty();
        assertThat(membershipRepository.findActive(ORGANIZATION_ID, new AccountId(7L)))
                .isEmpty();
    }

    @Test
    @DisplayName("두 행의 상태 변경 시각이 같으면 마지막 역할을 직원으로 본다")
    void treatsStaffAsLastRoleOnTie() {
        givenPending(31L, 7L, ParticipationType.TECHNICIAN);
        membershipRepository.given(
                ORGANIZATION_ID, new AccountId(7L), false, MembershipStatus.DEACTIVATED, REQUESTED_AT);
        givenTechnician(7L, TechnicianStatus.DEACTIVATED, REQUESTED_AT);

        service.approve(approve(OWNER, new Approval(31L, ParticipationType.TECHNICIAN)));

        verify(technicianRepository).reactivate(any(), org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    @DisplayName("비활성 기사 계약만 있는 회원을 직원으로 확정하면 기사 계약의 참여일을 이어받은 직원 소속을 만든다")
    void createsMembershipKeepingTechnicianJoinDate() {
        givenPending(31L, 7L, ParticipationType.TECHNICIAN);
        Technician deactivated = givenTechnician(7L, TechnicianStatus.DEACTIVATED, REQUESTED_AT);

        service.approve(approve(OWNER, new Approval(31L, ParticipationType.STAFF)));

        assertThat(membershipRepository.findActive(ORGANIZATION_ID, new AccountId(7L)))
                .hasValueSatisfying(membership -> {
                    assertThat(membership.isOwner()).isFalse();
                    assertThat(membership.joinedAt()).isEqualTo(deactivated.joinedAt());
                    assertThat(membership.statusChangedAt()).isEqualTo(NOW);
                });
        verify(technicianRepository, never()).reactivate(any(), anyBoolean());
        verify(technicianRepository, never()).save(any());
    }

    @Test
    @DisplayName("한 행이라도 활성이면 다른 행이 비활성이어도 승인하지 않는다")
    void treatsMixedRowsWithActiveOneAsMember() {
        givenPending(31L, 7L, ParticipationType.TECHNICIAN);
        membershipRepository.given(ORGANIZATION_ID, new AccountId(7L), false);
        givenTechnician(7L, TechnicianStatus.DEACTIVATED, REQUESTED_AT);

        assertThat(service.approve(approve(OWNER, new Approval(31L, ParticipationType.TECHNICIAN))))
                .extracting(ParticipationRequestDecisionInfo::outcome)
                .containsExactly(ParticipationRequestDecisionOutcome.ALREADY_MEMBER);
        verify(technicianRepository, never()).reactivate(any(), anyBoolean());
    }

    @Test
    @DisplayName("승인은 요청 행보다 발주사를 먼저 잠그고, 잠금 대기 한도를 넘기면 어떤 요청도 잠그지 않고 409 ORGANIZATION-010으로 거부한다")
    void locksOrganizationBeforeRequests() {
        givenPending(31L, 7L, ParticipationType.STAFF);

        service.approve(approve(OWNER, new Approval(31L, ParticipationType.STAFF)));

        InOrder order = inOrder(organizationRepository, requestRepository);
        order.verify(organizationRepository).lock(ORGANIZATION_ID);
        order.verify(requestRepository).findByIdForUpdate(new ParticipationRequestId(31L));

        org.mockito.Mockito.clearInvocations(requestRepository);
        doThrow(new OrganizationBusyException(new IllegalStateException("lock timeout")))
                .when(organizationRepository)
                .lock(ORGANIZATION_ID);
        assertRejected(
                () -> service.approve(approve(OWNER, new Approval(31L, ParticipationType.STAFF))),
                OrganizationErrorCode.ORGANIZATION_BUSY);
    }

    @Test
    @DisplayName("사유와 함께 거절하면 앞뒤 공백을 지운 사유와 처리자를 남기고 소속은 만들지 않는다")
    void rejectsWithStrippedReason() {
        givenPending(31L, 7L, ParticipationType.STAFF);
        givenPending(32L, 8L, ParticipationType.TECHNICIAN);

        List<ParticipationRequestDecisionInfo> results = service.reject(
                new RejectParticipationRequestsCommand(OWNER.value(), 10L, List.of(31L, 32L), "  인원 마감 "));

        assertThat(results)
                .extracting(ParticipationRequestDecisionInfo::outcome, ParticipationRequestDecisionInfo::status)
                .containsOnly(tuple(ParticipationRequestDecisionOutcome.REJECTED, ParticipationRequestStatus.REJECTED));
        ArgumentCaptor<ParticipationRequest> saved = ArgumentCaptor.forClass(ParticipationRequest.class);
        verify(requestRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(request -> {
            assertThat(request.rejectReason()).contains("인원 마감");
            assertThat(request.decidedBy()).isEqualTo(ownerMembership.id());
            assertThat(request.decidedAt()).contains(NOW);
        });
        assertThat(membershipRepository.saveCount()).isZero();
        verifyNoInteractions(technicianRepository, organizationRepository);
    }

    @Test
    @DisplayName("사유 없이 거절할 수 있다")
    void rejectsWithoutReason() {
        givenPending(31L, 7L, ParticipationType.STAFF);

        service.reject(new RejectParticipationRequestsCommand(OWNER.value(), 10L, List.of(31L), "  "));

        assertThat(savedRequest().rejectReason()).isEmpty();
    }

    @Test
    @DisplayName("거절 사유가 255자를 넘으면 어떤 요청도 잠그지 않고 ORGANIZATION-019로 거부한다")
    void refusesTooLongRejectReason() {
        assertRejected(
                () -> service.reject(
                        new RejectParticipationRequestsCommand(OWNER.value(), 10L, List.of(31L), "가".repeat(256))),
                OrganizationErrorCode.INVALID_REJECT_REASON);
    }

    @Test
    @DisplayName("총관리자가 아닌 직원은 403 ORGANIZATION-003, 소속이 없으면 403 ORGANIZATION-002로 거부한다")
    void refusesNonOwners() {
        assertRejected(
                () -> service.approve(approve(STAFF, new Approval(31L, ParticipationType.STAFF))),
                OrganizationErrorCode.OWNER_ONLY);
        assertRejected(
                () -> service.reject(new RejectParticipationRequestsCommand(9L, 10L, List.of(31L), null)),
                OrganizationErrorCode.NOT_ORGANIZATION_MEMBER);
    }

    @Test
    @DisplayName("빈 목록·중복 요청·0 이하 식별자·확정 유형 누락·한도 초과는 입력 오류다")
    void refusesMalformedBatches() {
        assertRejected(() -> service.approve(approve(OWNER)), CommonErrorCode.BAD_REQUEST);
        assertRejected(
                () -> service.approve(approve(
                        OWNER,
                        new Approval(31L, ParticipationType.STAFF),
                        new Approval(31L, ParticipationType.TECHNICIAN))),
                CommonErrorCode.BAD_REQUEST);
        assertRejected(
                () -> service.approve(approve(OWNER, new Approval(0L, ParticipationType.STAFF))),
                CommonErrorCode.BAD_REQUEST);
        assertRejected(() -> service.approve(approve(OWNER, new Approval(31L, null))), CommonErrorCode.BAD_REQUEST);
        assertRejected(
                () -> service.reject(new RejectParticipationRequestsCommand(
                        OWNER.value(),
                        10L,
                        LongStream.rangeClosed(1, DecideParticipationRequestsService.MAX_BATCH_SIZE + 1)
                                .boxed()
                                .toList(),
                        null)),
                CommonErrorCode.BAD_REQUEST);
    }

    private void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, BaseCode expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(expected));
        verify(requestRepository, never()).findByIdForUpdate(any());
        verify(requestRepository, never()).save(any());
    }

    private ParticipationRequest savedRequest() {
        ArgumentCaptor<ParticipationRequest> saved = ArgumentCaptor.forClass(ParticipationRequest.class);
        verify(requestRepository).save(saved.capture());
        return saved.getValue();
    }

    private void givenPending(long id, long accountId, ParticipationType desiredType) {
        requests.put(
                id,
                ParticipationRequest.reconstitute(
                        new ParticipationRequestId(id),
                        ORGANIZATION_ID,
                        new AccountId(accountId),
                        desiredType,
                        RequestChannel.COMPANY_CODE,
                        null,
                        ParticipationRequestStatus.PENDING,
                        null,
                        REQUESTED_AT,
                        null,
                        null));
    }

    private void givenDecided(long id, long accountId, ParticipationRequestStatus status) {
        boolean decided = status == ParticipationRequestStatus.REJECTED;
        requests.put(
                id,
                ParticipationRequest.reconstitute(
                        new ParticipationRequestId(id),
                        ORGANIZATION_ID,
                        new AccountId(accountId),
                        ParticipationType.STAFF,
                        RequestChannel.COMPANY_CODE,
                        null,
                        status,
                        null,
                        REQUESTED_AT,
                        decided ? NOW : null,
                        decided ? new MembershipId(99L) : null));
    }

    private Technician givenTechnician(long accountId, TechnicianStatus status, Instant statusChangedAt) {
        Technician technician = Technician.reconstitute(
                new TechnicianId(50L + accountId),
                ORGANIZATION_ID,
                new AccountId(accountId),
                status,
                REQUESTED_AT.minusSeconds(3600),
                statusChangedAt);
        when(technicianRepository.find(ORGANIZATION_ID, new AccountId(accountId)))
                .thenReturn(Optional.of(technician));
        return technician;
    }

    private static ApproveParticipationRequestsCommand approve(AccountId requester, Approval... approvals) {
        return new ApproveParticipationRequestsCommand(requester.value(), 10L, List.of(approvals));
    }
}
