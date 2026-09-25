package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.AcceptWorkCommand;

public interface AcceptWorkUseCase {

    void accept(AcceptWorkCommand command);
}
