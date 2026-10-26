package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.DeleteWorkTypeCommand;

public interface DeleteWorkTypeUseCase {

    void delete(DeleteWorkTypeCommand command);
}
