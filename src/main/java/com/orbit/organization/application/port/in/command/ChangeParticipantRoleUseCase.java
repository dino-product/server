package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.ChangeParticipantRoleCommand;
import com.orbit.organization.application.port.in.command.dto.ParticipantRoleChangeInfo;

public interface ChangeParticipantRoleUseCase {

    ParticipantRoleChangeInfo changeRole(ChangeParticipantRoleCommand command);
}
