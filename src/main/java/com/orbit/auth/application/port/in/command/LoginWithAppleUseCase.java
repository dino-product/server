package com.orbit.auth.application.port.in.command;

import com.orbit.auth.application.port.in.command.dto.LoginInfo;
import com.orbit.auth.application.port.in.command.dto.LoginWithAppleCommand;

public interface LoginWithAppleUseCase {

    LoginInfo login(LoginWithAppleCommand command);
}
