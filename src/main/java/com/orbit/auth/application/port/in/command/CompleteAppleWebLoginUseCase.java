package com.orbit.auth.application.port.in.command;

import com.orbit.auth.application.port.in.command.dto.CompleteAppleWebLoginCommand;
import com.orbit.auth.application.port.in.command.dto.WebLoginCompletion;

public interface CompleteAppleWebLoginUseCase {

    WebLoginCompletion complete(CompleteAppleWebLoginCommand command);
}
