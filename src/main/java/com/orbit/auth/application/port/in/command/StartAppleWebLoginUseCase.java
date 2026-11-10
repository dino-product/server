package com.orbit.auth.application.port.in.command;

import com.orbit.auth.application.port.in.command.dto.StartAppleWebLoginCommand;
import com.orbit.auth.application.port.in.command.dto.WebLoginStartInfo;

public interface StartAppleWebLoginUseCase {

    WebLoginStartInfo start(StartAppleWebLoginCommand command);
}
