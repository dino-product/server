package com.orbit.organization.application.port.in.query.dto;

import java.time.Instant;

import com.orbit.organization.application.port.out.AccountParticipationRequest;
import com.orbit.organization.domain.ParticipationRequest;
import com.orbit.organization.domain.ParticipationRequestStatus;
import com.orbit.organization.domain.ParticipationType;

/** 신청 현황의 한 줄. 확정 유형은 승인된 요청, 거절 사유는 사유가 입력된 거절 요청, 처리 일시는 승인·거절된 요청에만 있다. */
public record MyParticipationRequestInfo(
        Long requestId,
        Long organizationId,
        String organizationName,
        ParticipationType desiredType,
        ParticipationType confirmedType,
        ParticipationRequestStatus status,
        String rejectReason,
        Instant requestedAt,
        Instant decidedAt) {

    public static MyParticipationRequestInfo from(AccountParticipationRequest row) {
        ParticipationRequest request = row.request();
        return new MyParticipationRequestInfo(
                request.id().orElseThrow().value(),
                request.organizationId().value(),
                row.organizationName(),
                request.desiredType(),
                request.confirmedType().orElse(null),
                request.status(),
                request.rejectReason().orElse(null),
                request.requestedAt(),
                request.decidedAt().orElse(null));
    }
}
