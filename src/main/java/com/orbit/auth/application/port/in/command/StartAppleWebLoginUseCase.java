package com.orbit.auth.application.port.in.command;

import com.orbit.auth.application.port.in.command.dto.AppleWebLoginStartInfo;
import com.orbit.auth.application.port.in.command.dto.StartAppleWebLoginCommand;

public interface StartAppleWebLoginUseCase {

    AppleWebLoginStartInfo start(StartAppleWebLoginCommand command);
}
