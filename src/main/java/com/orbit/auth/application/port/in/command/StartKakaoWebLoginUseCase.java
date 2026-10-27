package com.orbit.auth.application.port.in.command;

import com.orbit.auth.application.port.in.command.dto.StartKakaoWebLoginCommand;
import com.orbit.auth.application.port.in.command.dto.WebLoginStartInfo;

public interface StartKakaoWebLoginUseCase {

    WebLoginStartInfo start(StartKakaoWebLoginCommand command);
}
