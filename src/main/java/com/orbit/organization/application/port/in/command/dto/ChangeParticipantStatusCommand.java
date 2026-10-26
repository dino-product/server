package com.orbit.organization.application.port.in.command.dto;

import com.orbit.organization.domain.ParticipationType;

/** {@code accountId}는 인증 주체에서 얻은 요청자 계정이다. {@code type}이 직원이면 {@code memberId}는 직원 소속, 기사면 기사 계약 식별자다. */
public record ChangeParticipantStatusCommand(
        Long accountId, Long organizationId, ParticipationType type, Long memberId) {}
