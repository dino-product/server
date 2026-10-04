package com.orbit.auth.application.port.in.command;

import com.orbit.auth.application.port.in.command.dto.ExchangeAppleWebLoginCommand;
import com.orbit.auth.application.port.in.command.dto.LoginInfo;

public interface ExchangeAppleWebLoginUseCase {

    LoginInfo exchange(ExchangeAppleWebLoginCommand command);
}
