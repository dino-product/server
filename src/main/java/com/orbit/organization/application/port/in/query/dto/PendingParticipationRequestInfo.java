package com.orbit.organization.application.port.in.query.dto;

import java.time.Instant;

import com.orbit.organization.domain.ParticipationRequest;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.organization.domain.RequestChannel;

/** 승인 대기 목록의 한 줄. 신청자 이름은 profile에서 빌려 오며, 가입을 마치지 않았거나 프로필이 없으면 없다({@code null}). */
public record PendingParticipationRequestInfo(
        Long requestId,
        Long accountId,
        String applicantName,
        ParticipationType desiredType,
        RequestChannel requestChannel,
        Instant requestedAt) {

    public static PendingParticipationRequestInfo of(ParticipationRequest request, String applicantName) {
        return new PendingParticipationRequestInfo(
                request.id().orElseThrow().value(),
                request.accountId().value(),
                applicantName,
                request.desiredType(),
                request.requestChannel(),
                request.requestedAt());
    }
}
