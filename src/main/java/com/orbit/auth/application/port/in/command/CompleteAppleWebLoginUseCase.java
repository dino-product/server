package com.orbit.auth.application.port.in.command;

import com.orbit.auth.application.port.in.command.dto.AppleWebLoginCompletion;
import com.orbit.auth.application.port.in.command.dto.CompleteAppleWebLoginCommand;

public interface CompleteAppleWebLoginUseCase {

    AppleWebLoginCompletion complete(CompleteAppleWebLoginCommand command);
}
