package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.ChangeParticipantStatusCommand;

public interface ReactivateParticipantUseCase {

    void reactivate(ChangeParticipantStatusCommand command);
}
