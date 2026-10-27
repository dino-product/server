package com.orbit.organization.application.port.in.command.dto;

import java.util.List;

import com.orbit.organization.domain.ParticipationType;

/** {@code accountId}는 인증 주체에서 얻은 요청자 계정이다. 행마다 확정 유형을 따로 정한다. */
public record ApproveParticipationRequestsCommand(Long accountId, Long organizationId, List<Approval> approvals) {

    public record Approval(Long requestId, ParticipationType confirmedType) {}
}
