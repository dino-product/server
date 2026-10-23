package com.orbit.auth.application.port.in.command;

import com.orbit.auth.application.port.in.command.dto.LogoutCommand;

public interface LogoutUseCase {

    /**
     * 현재 Access Token을 남은 유효 시간 동안 폐기하고 그 토큰을 발급한 기기 세션을 끝낸다. 이후 같은 토큰은 보호 자원에서 404를, 그 기기의 refresh token은
     * 재발급에서 거절을 받는다. 같은 계정의 다른 기기 세션은 그대로다.
     */
    void logout(LogoutCommand command);
}
