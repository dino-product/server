package com.orbit.auth.application.port.in.command;

import com.orbit.auth.application.port.in.command.dto.ExchangeWebLoginCommand;
import com.orbit.auth.application.port.in.command.dto.LoginInfo;

public interface ExchangeWebLoginUseCase {

    LoginInfo exchange(ExchangeWebLoginCommand command);
}
