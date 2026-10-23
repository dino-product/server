package com.orbit.auth.application.port.in.command;

import com.orbit.auth.application.port.in.command.dto.LogoutCommand;

public interface LogoutUseCase {

    /**
     * 유효한 Access Token이 있으면 남은 유효 시간 동안 폐기하고 그 토큰을 발급한 기기 세션을 끝낸다. refresh token이 함께 오면 비밀값이 맞는 그 세션도
     * 끝내므로 Access Token이 만료된 기기도 로그아웃할 수 있다. 끝낼 것이 없으면 아무것도 하지 않는다. 같은 계정의 다른 기기 세션은 그대로다.
     */
    void logout(LogoutCommand command);
}
