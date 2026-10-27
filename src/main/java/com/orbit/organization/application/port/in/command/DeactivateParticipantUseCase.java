package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.ChangeParticipantStatusCommand;

public interface DeactivateParticipantUseCase {

    void deactivate(ChangeParticipantStatusCommand command);
}
