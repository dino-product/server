package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.query.dto.ListPendingParticipationRequestsQuery;
import com.orbit.organization.application.port.in.query.dto.PendingParticipationRequestInfo;
import com.orbit.organization.application.port.out.ParticipationRequestQueryPort;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationRequest;
import com.orbit.organization.domain.ParticipationRequestId;
import com.orbit.organization.domain.ParticipationRequestStatus;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.organization.domain.RequestChannel;
import com.orbit.shared.error.BaseCode;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("참여 요청 승인 대기 목록 조회")
class ListPendingParticipationRequestsServiceTest {

    private static final Instant FIRST = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-10-02T00:00:00Z");
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(10L);
    private static final AccountId OWNER = new AccountId(1L);
    private static final AccountId STAFF = new AccountId(2L);

    @Mock
    private ParticipationRequestQueryPort queryPort;

    private InMemoryMembershipRepository membershipRepository;
    private ListPendingParticipationRequestsService service;

    @BeforeEach
    void setUp() {
        membershipRepository = new InMemoryMembershipRepository();
        membershipRepository.given(ORGANIZATION_ID, OWNER, true);
        membershipRepository.given(ORGANIZATION_ID, STAFF, false);
        service = new ListPendingParticipationRequestsService(membershipRepository, queryPort);
    }

    @Test
    @DisplayName("총관리자는 그 발주사의 승인 대기 요청을 저장소 순서대로 신청자·희망 유형·요청 일시와 함께 받는다")
    void ownerListsPendingRequests() {
        when(queryPort.listPending(ORGANIZATION_ID))
                .thenReturn(List.of(
                        pending(31L, 7L, ParticipationType.TECHNICIAN, FIRST),
                        pending(32L, 8L, ParticipationType.STAFF, SECOND)));

        List<PendingParticipationRequestInfo> result =
                service.list(new ListPendingParticipationRequestsQuery(OWNER.value(), ORGANIZATION_ID.value()));

        assertThat(result)
                .containsExactly(
                        new PendingParticipationRequestInfo(
                                31L, 7L, ParticipationType.TECHNICIAN, RequestChannel.COMPANY_CODE, FIRST),
                        new PendingParticipationRequestInfo(
                                32L, 8L, ParticipationType.STAFF, RequestChannel.COMPANY_CODE, SECOND));
    }

    @Test
    @DisplayName("총관리자가 아닌 직원은 조회할 수 없다")
    void rejectsStaff() {
        assertRejected(STAFF.value(), ORGANIZATION_ID.value(), OrganizationErrorCode.OWNER_ONLY);
    }

    @Test
    @DisplayName("그 발주사의 활성 직원 소속이 없으면(기사·다른 발주사·없는 발주사) 조회할 수 없다")
    void rejectsNonMember() {
        assertRejected(9L, ORGANIZATION_ID.value(), OrganizationErrorCode.NOT_ORGANIZATION_MEMBER);
        assertRejected(OWNER.value(), 11L, OrganizationErrorCode.NOT_ORGANIZATION_MEMBER);
    }

    @Test
    @DisplayName("0 이하의 발주사 식별자는 입력 오류다")
    void rejectsNonPositiveOrganizationId() {
        assertRejected(OWNER.value(), 0L, CommonErrorCode.BAD_REQUEST);
    }

    private void assertRejected(Long accountId, Long organizationId, BaseCode expected) {
        assertThatThrownBy(() -> service.list(new ListPendingParticipationRequestsQuery(accountId, organizationId)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(expected));
        verifyNoInteractions(queryPort);
    }

    private static ParticipationRequest pending(long id, long accountId, ParticipationType desiredType, Instant at) {
        return ParticipationRequest.reconstitute(
                new ParticipationRequestId(id),
                ORGANIZATION_ID,
                new AccountId(accountId),
                desiredType,
                RequestChannel.COMPANY_CODE,
                null,
                ParticipationRequestStatus.PENDING,
                null,
                at,
                null,
                null);
    }
}
