package com.orbit.auth.application.port.in.command;

import com.orbit.auth.application.port.in.command.dto.LoginInfo;
import com.orbit.auth.application.port.in.command.dto.LoginWithKakaoCommand;

public interface LoginWithKakaoUseCase {

    LoginInfo login(LoginWithKakaoCommand command);
}
