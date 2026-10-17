package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
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

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestInfo;
import com.orbit.organization.application.port.in.command.dto.RequestParticipationCommand;
import com.orbit.organization.application.port.out.AccountMembership;
import com.orbit.organization.application.port.out.AccountMembershipQueryPort;
import com.orbit.organization.application.port.out.CompanyCodeAttemptLimiter;
import com.orbit.organization.application.port.out.OrganizationBusyException;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.ParticipationRequestRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.MemberRole;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.OrganizationName;
import com.orbit.organization.domain.ParticipationRequest;
import com.orbit.organization.domain.ParticipationRequestId;
import com.orbit.organization.domain.ParticipationRequestStatus;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.shared.error.BaseCode;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("참여 요청 생성")
class RequestParticipationServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(10L);
    private static final AccountId ACCOUNT_ID = new AccountId(7L);
    private static final CompanyCode CODE = new CompanyCode("7K2M9X");

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private AccountMembershipQueryPort membershipQueryPort;

    @Mock
    private ParticipationRequestRepository requestRepository;

    @Mock
    private CompanyCodeAttemptLimiter attemptLimiter;

    private RequestParticipationService service;

    @BeforeEach
    void setUp() {
        lenient().when(attemptLimiter.tryAttempt(any())).thenReturn(true);
        service = new RequestParticipationService(
                organizationRepository,
                membershipQueryPort,
                requestRepository,
                attemptLimiter,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("정규화한 코드의 발주사 행을 잠그고 코드를 다시 확인한 뒤 희망 유형의 승인 대기 요청을 만든다")
    void createsPendingRequestAfterLockingOrganization() {
        when(organizationRepository.findByCode(CODE)).thenReturn(Optional.of(organization()));
        when(membershipQueryPort.listMemberships(ACCOUNT_ID)).thenReturn(List.of());
        when(requestRepository.findPending(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.empty());
        when(requestRepository.save(any(ParticipationRequest.class)))
                .thenAnswer(invocation -> saved(invocation.getArgument(0)));

        ParticipationRequestInfo result =
                service.request(new RequestParticipationCommand(7L, "7k2m-9x", ParticipationType.TECHNICIAN));

        assertThat(result)
                .isEqualTo(new ParticipationRequestInfo(
                        30L, 10L, "오르빗 설비", ParticipationType.TECHNICIAN, ParticipationRequestStatus.PENDING, NOW));
        InOrder order = inOrder(organizationRepository, membershipQueryPort, requestRepository);
        order.verify(organizationRepository).findByCode(CODE);
        order.verify(organizationRepository).lock(ORGANIZATION_ID);
        order.verify(organizationRepository).findByCode(CODE);
        order.verify(membershipQueryPort).listMemberships(ACCOUNT_ID);
        order.verify(requestRepository).findPending(ORGANIZATION_ID, ACCOUNT_ID);
        ArgumentCaptor<ParticipationRequest> request = ArgumentCaptor.forClass(ParticipationRequest.class);
        order.verify(requestRepository).save(request.capture());
        assertThat(request.getValue().accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(request.getValue().organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(request.getValue().isPending()).isTrue();
    }

    @Test
    @DisplayName("잠금을 기다리는 사이 회사 코드가 바뀌었으면 이전 코드로 거부한다")
    void rejectsCodeChangedWhileWaitingForLock() {
        when(organizationRepository.findByCode(CODE)).thenReturn(Optional.of(organization()), Optional.empty());
        when(organizationRepository.existsRetiredCode(CODE)).thenReturn(true);

        assertRejected("7K2M9X", OrganizationErrorCode.RETIRED_COMPANY_CODE);
        verify(organizationRepository).lock(ORGANIZATION_ID);
        verifyNoInteractions(membershipQueryPort, requestRepository);
    }

    @Test
    @DisplayName("앞선 변경이 발주사 잠금 대기 한도 안에 끝나지 않으면 요청을 만들지 않고 409로 거부한다")
    void rejectsWhenOrganizationIsBusy() {
        lockedOrganization();
        doThrow(new OrganizationBusyException(new IllegalStateException("lock timeout")))
                .when(organizationRepository)
                .lock(ORGANIZATION_ID);

        assertRejected("7K2M9X", OrganizationErrorCode.ORGANIZATION_BUSY);
        verifyNoInteractions(membershipQueryPort, requestRepository);
    }

    @Test
    @DisplayName("폐기된 이전 코드로는 요청을 만들지 않는다")
    void rejectsRetiredCode() {
        when(organizationRepository.findByCode(CODE)).thenReturn(Optional.empty());
        when(organizationRepository.existsRetiredCode(CODE)).thenReturn(true);

        assertRejected("7K2M9X", OrganizationErrorCode.RETIRED_COMPANY_CODE);
        verify(organizationRepository, never()).lock(any());
        verifyNoInteractions(requestRepository);
        verify(attemptLimiter).refund(ACCOUNT_ID);
    }

    @Test
    @DisplayName("없는 코드로는 요청을 만들지 않는다")
    void rejectsUnknownCode() {
        when(organizationRepository.findByCode(CODE)).thenReturn(Optional.empty());

        assertRejected("7K2M9X", OrganizationErrorCode.COMPANY_NOT_FOUND);
        verifyNoInteractions(requestRepository);
        verify(attemptLimiter, never()).refund(any());
    }

    @Test
    @DisplayName("회사 확인 실패가 한도에 이른 계정은 코드를 찾아보지 않고 거부한다")
    void rejectsBlockedAccountWithoutLookingUpCode() {
        when(attemptLimiter.tryAttempt(ACCOUNT_ID)).thenReturn(false);

        assertRejected("7K2M9X", OrganizationErrorCode.TOO_MANY_COMPANY_CODE_ATTEMPTS);
        verifyNoInteractions(organizationRepository, membershipQueryPort, requestRepository);
        verify(attemptLimiter, never()).refund(any());
    }

    @Test
    @DisplayName("같은 발주사에 활성 소속이 있으면 이미 참여 중으로 거부한다")
    void rejectsActiveMember() {
        lockedOrganization();
        when(membershipQueryPort.listMemberships(ACCOUNT_ID)).thenReturn(List.of(membership(MemberRole.STAFF, true)));

        assertRejected("7K2M9X", OrganizationErrorCode.ALREADY_MEMBER);
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 발주사에 비활성 소속만 있으면 기획 결정 전까지 요청을 막는다")
    void rejectsDeactivatedMember() {
        lockedOrganization();
        when(membershipQueryPort.listMemberships(ACCOUNT_ID))
                .thenReturn(List.of(membership(MemberRole.TECHNICIAN, false)));

        assertRejected("7K2M9X", OrganizationErrorCode.DEACTIVATED_MEMBER);
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 발주사에 승인 대기 요청이 있으면 새로 만들지 않는다")
    void rejectsSecondPendingRequest() {
        lockedOrganization();
        when(membershipQueryPort.listMemberships(ACCOUNT_ID)).thenReturn(List.of());
        when(requestRepository.findPending(ORGANIZATION_ID, ACCOUNT_ID))
                .thenReturn(Optional.of(saved(ParticipationRequest.request(
                        ORGANIZATION_ID, ACCOUNT_ID, ParticipationType.STAFF, CREATED_AT))));

        assertRejected("7K2M9X", OrganizationErrorCode.PENDING_REQUEST_EXISTS);
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("희망 유형이 없으면 발주사를 찾지 않고 거부한다")
    void rejectsMissingDesiredType() {
        assertThatThrownBy(() -> service.request(new RequestParticipationCommand(7L, "7K2M9X", null)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.BAD_REQUEST));
        verifyNoInteractions(organizationRepository, membershipQueryPort, requestRepository, attemptLimiter);
    }

    private void lockedOrganization() {
        when(organizationRepository.findByCode(CODE)).thenReturn(Optional.of(organization()));
    }

    private void assertRejected(String companyCode, BaseCode errorCode) {
        assertThatThrownBy(() ->
                        service.request(new RequestParticipationCommand(7L, companyCode, ParticipationType.STAFF)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(errorCode));
    }

    private static ParticipationRequest saved(ParticipationRequest request) {
        return ParticipationRequest.reconstitute(
                new ParticipationRequestId(30L),
                request.organizationId(),
                request.accountId(),
                request.desiredType(),
                null,
                request.status(),
                null,
                request.requestedAt(),
                null);
    }

    private static AccountMembership membership(MemberRole role, boolean active) {
        return new AccountMembership(ORGANIZATION_ID, "오르빗 설비", role, active, CREATED_AT, CREATED_AT);
    }

    private static Organization organization() {
        return Organization.reconstitute(ORGANIZATION_ID, new OrganizationName("오르빗 설비"), Industry.PLUMBING, CODE);
    }
}
