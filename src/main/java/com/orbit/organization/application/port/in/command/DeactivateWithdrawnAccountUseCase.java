package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.DeactivateWithdrawnAccountCommand;

public interface DeactivateWithdrawnAccountUseCase {

    void deactivate(DeactivateWithdrawnAccountCommand command);
}
