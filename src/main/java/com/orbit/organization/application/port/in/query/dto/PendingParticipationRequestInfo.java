package com.orbit.organization.application.port.in.query.dto;

import java.time.Instant;

import com.orbit.organization.domain.ParticipationRequest;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.organization.domain.RequestChannel;

/** 승인 대기 목록의 한 줄. 신청자는 계정 식별자로만 준다(이름은 profile 계약이 생기면 연결). */
public record PendingParticipationRequestInfo(
        Long requestId,
        Long accountId,
        ParticipationType desiredType,
        RequestChannel requestChannel,
        Instant requestedAt) {

    public static PendingParticipationRequestInfo from(ParticipationRequest request) {
        return new PendingParticipationRequestInfo(
                request.id().orElseThrow().value(),
                request.accountId().value(),
                request.desiredType(),
                request.requestChannel(),
                request.requestedAt());
    }
}
