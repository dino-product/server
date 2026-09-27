package com.orbit.auth.application.port.in.command;

import com.orbit.auth.application.port.in.command.dto.LogoutCommand;

public interface LogoutUseCase {

    /** 현재 Access Token을 남은 유효 시간 동안 폐기한다. 이후 같은 토큰은 보호 자원에서 404를 받는다. */
    void logout(LogoutCommand command);
}
