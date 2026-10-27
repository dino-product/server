package com.orbit.organization.application.port.in.command.dto;

import java.util.List;

/** {@code accountId}는 인증 주체에서 얻은 요청자 계정이다. 사유는 모든 행에 같이 남기며 없으면 {@code null}이다. */
public record RejectParticipationRequestsCommand(
        Long accountId, Long organizationId, List<Long> requestIds, String reason) {}
