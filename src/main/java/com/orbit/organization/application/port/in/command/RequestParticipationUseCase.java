package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.ParticipationRequestInfo;
import com.orbit.organization.application.port.in.command.dto.RequestParticipationCommand;

public interface RequestParticipationUseCase {

    ParticipationRequestInfo request(RequestParticipationCommand command);
}
