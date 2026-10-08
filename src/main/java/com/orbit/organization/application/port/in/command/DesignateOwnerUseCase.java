package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.DesignateOwnerCommand;

public interface DesignateOwnerUseCase {

    void designate(DesignateOwnerCommand command);
}
