package com.orbit.auth.application.port.in.command;

import com.orbit.auth.application.port.in.command.dto.RefreshTokensCommand;
import com.orbit.auth.application.port.in.command.dto.RefreshedTokensInfo;

public interface RefreshTokensUseCase {

    /**
     * refresh token으로 같은 기기 세션의 새 Access Token과 다음 refresh token을 발급하고 제출된 토큰을 무효로 한다. 이미 회전된 토큰이 다시 오면 그 세션을
     * 철회한다.
     */
    RefreshedTokensInfo refresh(RefreshTokensCommand command);
}
