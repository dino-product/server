package com.orbit.auth.application.port.in.command;

import com.orbit.auth.application.port.in.command.dto.CompleteKakaoWebLoginCommand;
import com.orbit.auth.application.port.in.command.dto.WebLoginCompletion;

public interface CompleteKakaoWebLoginUseCase {

    WebLoginCompletion complete(CompleteKakaoWebLoginCommand command);
}
