package com.orbit.organization.application.port.out;

import com.orbit.organization.domain.ParticipationRequest;

/** 계정의 참여 요청 하나와 그 발주사의 현재 이름. */
public record AccountParticipationRequest(ParticipationRequest request, String organizationName) {}
