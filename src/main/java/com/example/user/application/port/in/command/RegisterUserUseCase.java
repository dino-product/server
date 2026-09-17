package com.example.user.application.port.in.command;

import com.example.user.application.port.in.command.dto.RegisterUserCommand;
import com.example.user.application.port.in.command.dto.RegisteredUserInfo;

public interface RegisterUserUseCase {

    RegisteredUserInfo register(RegisterUserCommand command);
}
