package com.orbit.organization.application.port.in.command.dto;

import com.orbit.organization.domain.ParticipationRequestStatus;

/** 요청 한 건의 처리 결과와 처리 뒤 요청 상태. 찾을 수 없는 요청은 상태가 없다. */
public record ParticipationRequestDecisionInfo(
        Long requestId, ParticipationRequestDecisionOutcome outcome, ParticipationRequestStatus status) {}
