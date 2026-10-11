package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.dto.CancelParticipationRequestCommand;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestInfo;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.ParticipationRequestRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Industry;
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
@DisplayName("참여 요청 취소")
class CancelParticipationRequestServiceTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-10-06T00:00:00Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-10-07T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");
    private static final ParticipationRequestId REQUEST_ID = new ParticipationRequestId(30L);
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(10L);
    private static final AccountId ACCOUNT_ID = new AccountId(7L);

    @Mock
    private ParticipationRequestRepository requestRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    private CancelParticipationRequestService service;

    @BeforeEach
    void setUp() {
        service = new CancelParticipationRequestService(
                requestRepository, organizationRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("요청자가 승인 대기 요청을 잠가 읽고 취소 상태로 저장한다")
    void cancelsOwnPendingRequest() {
        when(requestRepository.findByIdForUpdate(REQUEST_ID))
                .thenReturn(Optional.of(request(ParticipationRequestStatus.PENDING, null)));
        when(requestRepository.save(any(ParticipationRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization()));

        ParticipationRequestInfo result = service.cancel(new CancelParticipationRequestCommand(7L, 30L));

        assertThat(result)
                .isEqualTo(new ParticipationRequestInfo(
                        30L,
                        10L,
                        "오르빗 설비",
                        ParticipationType.STAFF,
                        ParticipationRequestStatus.CANCELED,
                        REQUESTED_AT));
        ArgumentCaptor<ParticipationRequest> saved = ArgumentCaptor.forClass(ParticipationRequest.class);
        verify(requestRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(ParticipationRequestStatus.CANCELED);
        assertThat(saved.getValue().updatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("다른 계정의 요청은 본인 요청이 아니라며 거부한다")
    void rejectsOtherAccountsRequest() {
        when(requestRepository.findByIdForUpdate(REQUEST_ID))
                .thenReturn(Optional.of(request(ParticipationRequestStatus.PENDING, null)));

        assertRejected(new CancelParticipationRequestCommand(8L, 30L), OrganizationErrorCode.NOT_REQUESTER);
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("없는 요청은 다른 계정의 요청과 같은 오류로 거부해 존재 여부를 드러내지 않는다")
    void rejectsMissingRequestLikeOthersRequest() {
        when(requestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.empty());

        assertRejected(new CancelParticipationRequestCommand(7L, 30L), OrganizationErrorCode.NOT_REQUESTER);
    }

    @Test
    @DisplayName("이미 처리되었거나 취소된 요청은 취소할 수 없다")
    void rejectsRequestThatIsNotPending() {
        when(requestRepository.findByIdForUpdate(REQUEST_ID))
                .thenReturn(Optional.of(request(ParticipationRequestStatus.REJECTED, DECIDED_AT)));

        assertRejected(new CancelParticipationRequestCommand(7L, 30L), OrganizationErrorCode.REQUEST_NOT_PENDING);
        verify(requestRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    @DisplayName("요청 식별자 형식이 틀리면 저장소를 보지 않고 거부한다")
    void rejectsMalformedRequestId(Long requestId) {
        assertRejected(new CancelParticipationRequestCommand(7L, requestId), CommonErrorCode.BAD_REQUEST);
        verifyNoInteractions(requestRepository, organizationRepository);
    }

    private void assertRejected(CancelParticipationRequestCommand command, BaseCode errorCode) {
        assertThatThrownBy(() -> service.cancel(command))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(errorCode));
    }

    private static ParticipationRequest request(ParticipationRequestStatus status, Instant decidedAt) {
        return ParticipationRequest.reconstitute(
                REQUEST_ID,
                ORGANIZATION_ID,
                ACCOUNT_ID,
                ParticipationType.STAFF,
                null,
                status,
                null,
                REQUESTED_AT,
                decidedAt,
                decidedAt == null ? REQUESTED_AT : decidedAt);
    }

    private static Organization organization() {
        return Organization.reconstitute(
                ORGANIZATION_ID,
                new OrganizationName("오르빗 설비"),
                Industry.PLUMBING,
                new CompanyCode("7K2M9X"),
                REQUESTED_AT,
                REQUESTED_AT);
    }
}
