package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
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
import com.orbit.organization.application.port.out.AccountMembership;
import com.orbit.organization.application.port.out.AccountMembershipQueryPort;
import com.orbit.organization.application.port.out.ParticipationRequestRepository;
import com.orbit.organization.application.port.out.TechnicianRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MemberRole;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationRequest;
import com.orbit.organization.domain.ParticipationRequestId;
import com.orbit.organization.domain.ParticipationRequestStatus;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.organization.domain.Technician;
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
    private AccountMembershipQueryPort membershipQueryPort;

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
                membershipQueryPort,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient()
                .when(requestRepository.findByIdForUpdate(any()))
                .thenAnswer(invocation -> Optional.ofNullable(requests.get(
                        invocation.<ParticipationRequestId>getArgument(0).value())));
        lenient().when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(technicianRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
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
    @DisplayName("신청자에게 이미 소속 행이 있어 승인하지 못하면 이벤트를 발행하지 않는다")
    void publishesNothingWhenApprovalIsSkipped() {
        givenPending(31L, 7L, ParticipationType.STAFF);
        when(membershipQueryPort.listMemberships(new AccountId(7L)))
                .thenReturn(List.of(new AccountMembership(
                        ORGANIZATION_ID, "오르빗 설비", MemberRole.STAFF, false, REQUESTED_AT, REQUESTED_AT)));

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
    @DisplayName("신청자에게 그 발주사의 직원 소속·기사 계약 행이 이미 있으면 재활성화하지 않고 그 행만 실패로 둔다")
    void leavesRequestPendingWhenApplicantAlreadyHasRow() {
        givenPending(31L, 7L, ParticipationType.TECHNICIAN);
        when(membershipQueryPort.listMemberships(new AccountId(7L)))
                .thenReturn(List.of(new AccountMembership(
                        ORGANIZATION_ID, "오르빗 설비", MemberRole.TECHNICIAN, false, REQUESTED_AT, REQUESTED_AT)));

        List<ParticipationRequestDecisionInfo> results =
                service.approve(approve(OWNER, new Approval(31L, ParticipationType.TECHNICIAN)));

        assertThat(results)
                .containsExactly(new ParticipationRequestDecisionInfo(
                        31L, ParticipationRequestDecisionOutcome.ALREADY_MEMBER, ParticipationRequestStatus.PENDING));
        verify(requestRepository, never()).save(any());
        verify(technicianRepository, never()).save(any());
    }

    @Test
    @DisplayName("다른 발주사의 소속은 승인을 막지 않는다")
    void ignoresRowsOfOtherOrganizations() {
        givenPending(31L, 7L, ParticipationType.STAFF);
        when(membershipQueryPort.listMemberships(new AccountId(7L)))
                .thenReturn(List.of(new AccountMembership(
                        OTHER_ORGANIZATION_ID, "다른 설비", MemberRole.STAFF, true, REQUESTED_AT, REQUESTED_AT)));

        assertThat(service.approve(approve(OWNER, new Approval(31L, ParticipationType.STAFF))))
                .extracting(ParticipationRequestDecisionInfo::outcome)
                .containsExactly(ParticipationRequestDecisionOutcome.APPROVED);
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
        verifyNoInteractions(technicianRepository, membershipQueryPort);
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
                        null,
                        status,
                        null,
                        REQUESTED_AT,
                        decided ? NOW : null,
                        decided ? new MembershipId(99L) : null));
    }

    private static ApproveParticipationRequestsCommand approve(AccountId requester, Approval... approvals) {
        return new ApproveParticipationRequestsCommand(requester.value(), 10L, List.of(approvals));
    }
}
