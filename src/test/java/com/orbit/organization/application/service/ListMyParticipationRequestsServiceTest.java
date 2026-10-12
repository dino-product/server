package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.application.port.in.query.dto.ListMyParticipationRequestsQuery;
import com.orbit.organization.application.port.in.query.dto.MyParticipationRequestInfo;
import com.orbit.organization.application.port.out.AccountParticipationRequest;
import com.orbit.organization.application.port.out.ParticipationRequestQueryPort;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationRequest;
import com.orbit.organization.domain.ParticipationRequestId;
import com.orbit.organization.domain.ParticipationRequestStatus;
import com.orbit.organization.domain.ParticipationType;

@ExtendWith(MockitoExtension.class)
@DisplayName("내 신청 현황 조회")
class ListMyParticipationRequestsServiceTest {

    private static final Instant FIRST = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-10-02T00:00:00Z");
    private static final Instant THIRD = Instant.parse("2026-10-03T00:00:00Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-10-04T00:00:00Z");
    private static final AccountId ACCOUNT_ID = new AccountId(7L);

    @Mock
    private ParticipationRequestQueryPort queryPort;

    private ListMyParticipationRequestsService service;

    @BeforeEach
    void setUp() {
        service = new ListMyParticipationRequestsService(queryPort);
    }

    @Test
    @DisplayName("승인 대기·승인·거절·취소 요청을 모두 요청 일시순으로 돌려주고 상태에 맞는 결과만 채운다")
    void listsAllRequestsInRequestOrderWithResults() {
        when(queryPort.listByAccount(ACCOUNT_ID))
                .thenReturn(List.of(
                        row(
                                31L,
                                ParticipationType.TECHNICIAN,
                                ParticipationType.STAFF,
                                ParticipationRequestStatus.APPROVED,
                                null,
                                FIRST,
                                DECIDED_AT),
                        row(
                                32L,
                                ParticipationType.STAFF,
                                null,
                                ParticipationRequestStatus.REJECTED,
                                "인원 마감",
                                SECOND,
                                DECIDED_AT),
                        row(33L, ParticipationType.STAFF, null, ParticipationRequestStatus.CANCELED, null, THIRD, null),
                        row(
                                34L,
                                ParticipationType.TECHNICIAN,
                                null,
                                ParticipationRequestStatus.PENDING,
                                null,
                                THIRD,
                                null)));

        List<MyParticipationRequestInfo> result = service.list(new ListMyParticipationRequestsQuery(7L));

        assertThat(result)
                .containsExactly(
                        new MyParticipationRequestInfo(
                                31L,
                                10L,
                                "오르빗 설비",
                                ParticipationType.TECHNICIAN,
                                ParticipationType.STAFF,
                                ParticipationRequestStatus.APPROVED,
                                null,
                                FIRST,
                                DECIDED_AT),
                        new MyParticipationRequestInfo(
                                32L,
                                10L,
                                "오르빗 설비",
                                ParticipationType.STAFF,
                                null,
                                ParticipationRequestStatus.REJECTED,
                                "인원 마감",
                                SECOND,
                                DECIDED_AT),
                        new MyParticipationRequestInfo(
                                33L,
                                10L,
                                "오르빗 설비",
                                ParticipationType.STAFF,
                                null,
                                ParticipationRequestStatus.CANCELED,
                                null,
                                THIRD,
                                null),
                        new MyParticipationRequestInfo(
                                34L,
                                10L,
                                "오르빗 설비",
                                ParticipationType.TECHNICIAN,
                                null,
                                ParticipationRequestStatus.PENDING,
                                null,
                                THIRD,
                                null));
    }

    @Test
    @DisplayName("보낸 요청이 없으면 빈 목록이다")
    void returnsEmptyListWithoutRequests() {
        when(queryPort.listByAccount(ACCOUNT_ID)).thenReturn(List.of());

        assertThat(service.list(new ListMyParticipationRequestsQuery(7L))).isEmpty();
    }

    private static AccountParticipationRequest row(
            Long id,
            ParticipationType desiredType,
            ParticipationType confirmedType,
            ParticipationRequestStatus status,
            String rejectReason,
            Instant requestedAt,
            Instant decidedAt) {
        return new AccountParticipationRequest(
                ParticipationRequest.reconstitute(
                        new ParticipationRequestId(id),
                        new OrganizationId(10L),
                        ACCOUNT_ID,
                        desiredType,
                        confirmedType,
                        status,
                        rejectReason,
                        requestedAt,
                        decidedAt),
                "오르빗 설비");
    }
}
