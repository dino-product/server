package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.query.dto.CheckCompanyQuery;
import com.orbit.organization.application.port.in.query.dto.CompanyCheckInfo;
import com.orbit.organization.application.port.in.query.dto.ParticipationAvailability;
import com.orbit.organization.application.port.out.AccountMembership;
import com.orbit.organization.application.port.out.AccountMembershipQueryPort;
import com.orbit.organization.application.port.out.CompanyCodeAttemptLimiter;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("회사 확인")
class CheckCompanyServiceTest {

    private static final Instant AT = Instant.parse("2026-10-06T00:00:00Z");
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(10L);
    private static final OrganizationId OTHER_ORGANIZATION_ID = new OrganizationId(11L);
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

    private CheckCompanyService service;

    @BeforeEach
    void setUp() {
        lenient().when(attemptLimiter.tryAttempt(any())).thenReturn(true);
        service =
                new CheckCompanyService(organizationRepository, membershipQueryPort, requestRepository, attemptLimiter);
    }

    @Test
    @DisplayName("소문자·하이픈·공백이 섞인 입력을 정규화해 회사명과 참여 가능 상태를 돌려준다")
    void normalizesInputAndReturnsAvailableCompany() {
        when(organizationRepository.findByCode(CODE)).thenReturn(Optional.of(organization()));
        when(membershipQueryPort.listMemberships(ACCOUNT_ID)).thenReturn(List.of());
        when(requestRepository.findPending(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.empty());

        CompanyCheckInfo result = service.check(new CheckCompanyQuery(7L, " 7k2m-9x "));

        assertThat(result).isEqualTo(new CompanyCheckInfo(10L, "오르빗 설비", ParticipationAvailability.AVAILABLE, null));
        verify(attemptLimiter).refund(ACCOUNT_ID);
    }

    @Test
    @DisplayName("다른 발주사의 소속은 이 발주사의 참여 여부에 영향을 주지 않는다")
    void ignoresMembershipsOfOtherOrganizations() {
        when(organizationRepository.findByCode(CODE)).thenReturn(Optional.of(organization()));
        when(membershipQueryPort.listMemberships(ACCOUNT_ID))
                .thenReturn(List.of(membership(OTHER_ORGANIZATION_ID, MemberRole.TECHNICIAN, true)));
        when(requestRepository.findPending(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThat(service.check(new CheckCompanyQuery(7L, "7K2M9X")).availability())
                .isEqualTo(ParticipationAvailability.AVAILABLE);
    }

    @Test
    @DisplayName("활성 기사 계약이 있으면 이미 참여 중이다")
    void reportsAlreadyMemberWithActiveTechnicianContract() {
        when(organizationRepository.findByCode(CODE)).thenReturn(Optional.of(organization()));
        when(membershipQueryPort.listMemberships(ACCOUNT_ID))
                .thenReturn(List.of(membership(ORGANIZATION_ID, MemberRole.TECHNICIAN, true)));

        assertThat(service.check(new CheckCompanyQuery(7L, "7K2M9X")).availability())
                .isEqualTo(ParticipationAvailability.ALREADY_MEMBER);
    }

    @Test
    @DisplayName("역할 변경으로 끝난 행이 있어도 활성 행이 있으면 이미 참여 중이다")
    void reportsAlreadyMemberAfterRoleChange() {
        when(organizationRepository.findByCode(CODE)).thenReturn(Optional.of(organization()));
        when(membershipQueryPort.listMemberships(ACCOUNT_ID))
                .thenReturn(List.of(
                        membership(ORGANIZATION_ID, MemberRole.STAFF, false),
                        membership(ORGANIZATION_ID, MemberRole.TECHNICIAN, true)));

        assertThat(service.check(new CheckCompanyQuery(7L, "7K2M9X")).availability())
                .isEqualTo(ParticipationAvailability.ALREADY_MEMBER);
    }

    @Test
    @DisplayName("비활성 소속만 있으면 비활성 소속 상태로 알린다")
    void reportsDeactivatedMemberWithOnlyInactiveRows() {
        when(organizationRepository.findByCode(CODE)).thenReturn(Optional.of(organization()));
        when(membershipQueryPort.listMemberships(ACCOUNT_ID))
                .thenReturn(List.of(membership(ORGANIZATION_ID, MemberRole.STAFF, false)));

        assertThat(service.check(new CheckCompanyQuery(7L, "7K2M9X")).availability())
                .isEqualTo(ParticipationAvailability.DEACTIVATED_MEMBER);
    }

    @Test
    @DisplayName("승인 대기 요청이 있으면 그 요청 식별자와 함께 알린다")
    void reportsPendingRequestWithItsId() {
        when(organizationRepository.findByCode(CODE)).thenReturn(Optional.of(organization()));
        when(membershipQueryPort.listMemberships(ACCOUNT_ID)).thenReturn(List.of());
        when(requestRepository.findPending(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(pendingRequest()));

        assertThat(service.check(new CheckCompanyQuery(7L, "7K2M9X")))
                .isEqualTo(new CompanyCheckInfo(10L, "오르빗 설비", ParticipationAvailability.PENDING_REQUEST, 30L));
    }

    @Test
    @DisplayName("현재 코드도 폐기 코드도 아니면 회사 확인 실패로 거부한다")
    void rejectsUnknownCode() {
        when(organizationRepository.findByCode(CODE)).thenReturn(Optional.empty());
        when(organizationRepository.existsRetiredCode(CODE)).thenReturn(false);

        assertRejected("7K2M9X", OrganizationErrorCode.COMPANY_NOT_FOUND);
        verify(attemptLimiter, never()).refund(any());
    }

    @Test
    @DisplayName("폐기된 이전 코드는 없는 코드와 구분해 이전 코드로 거부한다")
    void rejectsRetiredCodeSeparately() {
        when(organizationRepository.findByCode(CODE)).thenReturn(Optional.empty());
        when(organizationRepository.existsRetiredCode(CODE)).thenReturn(true);

        assertRejected("7k2m-9x", OrganizationErrorCode.RETIRED_COMPANY_CODE);
        verifyNoInteractions(membershipQueryPort, requestRepository);
        verify(attemptLimiter).refund(ACCOUNT_ID);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "7K2M9", "7K2M9XA", "7K2M9I", "7K2M9!"})
    @DisplayName("형식이 틀린 입력은 저장소를 보지 않고 없는 코드와 같게 거부한다")
    void rejectsMalformedCodeAsUnknown(String input) {
        assertRejected(input, OrganizationErrorCode.COMPANY_NOT_FOUND);
        verifyNoInteractions(organizationRepository, membershipQueryPort, requestRepository);
        verify(attemptLimiter, never()).refund(any());
    }

    @Test
    @DisplayName("실패가 한도에 이른 계정은 코드를 찾아보지 않고 거부해 코드의 존재 여부를 드러내지 않는다")
    void rejectsBlockedAccountWithoutLookingUpCode() {
        when(attemptLimiter.tryAttempt(ACCOUNT_ID)).thenReturn(false);

        assertRejected("7K2M9X", OrganizationErrorCode.TOO_MANY_COMPANY_CODE_ATTEMPTS);
        verifyNoInteractions(organizationRepository, membershipQueryPort, requestRepository);
        verify(attemptLimiter, never()).refund(any());
    }

    private void assertRejected(String input, BaseCode errorCode) {
        assertThatThrownBy(() -> service.check(new CheckCompanyQuery(7L, input)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(errorCode));
    }

    private static AccountMembership membership(OrganizationId organizationId, MemberRole role, boolean active) {
        return new AccountMembership(organizationId, "발주사", role, active, AT, AT);
    }

    private static ParticipationRequest pendingRequest() {
        return ParticipationRequest.reconstitute(
                new ParticipationRequestId(30L),
                ORGANIZATION_ID,
                ACCOUNT_ID,
                ParticipationType.TECHNICIAN,
                null,
                ParticipationRequestStatus.PENDING,
                null,
                AT,
                null,
                null);
    }

    private static Organization organization() {
        return Organization.reconstitute(ORGANIZATION_ID, new OrganizationName("오르빗 설비"), Industry.PLUMBING, CODE);
    }
}
