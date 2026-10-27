package com.orbit.organization.application.port.in.command;

import java.util.List;

import com.orbit.organization.application.port.in.command.dto.ParticipationRequestDecisionInfo;
import com.orbit.organization.application.port.in.command.dto.RejectParticipationRequestsCommand;

public interface RejectParticipationRequestsUseCase {

    /** 입력 순서대로 행별 결과를 돌려준다. */
    List<ParticipationRequestDecisionInfo> reject(RejectParticipationRequestsCommand command);
}
