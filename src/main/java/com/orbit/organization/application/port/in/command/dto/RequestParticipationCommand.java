package com.orbit.organization.application.port.in.command.dto;

import com.orbit.organization.domain.ParticipationType;
import com.orbit.organization.domain.RequestChannel;

/** {@code companyCode}는 사용자가 입력한 그대로다. 정규화는 서비스가 한다. {@code requestChannel}은 요청자가 회사를 찾은 경로다. */
public record RequestParticipationCommand(
        Long accountId, String companyCode, ParticipationType desiredType, RequestChannel requestChannel) {}
