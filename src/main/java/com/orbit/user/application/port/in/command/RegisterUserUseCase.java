package com.orbit.user.application.port.in.command;

import com.orbit.user.application.port.in.command.dto.RegisterUserCommand;
import com.orbit.user.application.port.in.command.dto.RegisteredUserInfo;

public interface RegisterUserUseCase {

    RegisteredUserInfo register(RegisterUserCommand command);
}
