package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.StartWorkCommand;

public interface StartWorkUseCase {

    void start(StartWorkCommand command);
}
