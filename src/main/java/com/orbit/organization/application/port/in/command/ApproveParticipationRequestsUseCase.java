package com.orbit.organization.application.port.in.command;

import java.util.List;

import com.orbit.organization.application.port.in.command.dto.ApproveParticipationRequestsCommand;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestDecisionInfo;

public interface ApproveParticipationRequestsUseCase {

    /** 입력 순서대로 행별 결과를 돌려준다. */
    List<ParticipationRequestDecisionInfo> approve(ApproveParticipationRequestsCommand command);
}
