package com.orbit.profile.application.port.in.command;

import com.orbit.profile.application.port.in.command.dto.SaveProfileCommand;

public interface SaveProfileUseCase {

    void save(SaveProfileCommand command);
}
