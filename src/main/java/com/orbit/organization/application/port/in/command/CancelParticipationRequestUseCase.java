package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.CancelParticipationRequestCommand;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestInfo;

public interface CancelParticipationRequestUseCase {

    ParticipationRequestInfo cancel(CancelParticipationRequestCommand command);
}
