package com.orbit.auth.application.port.in.command;

import com.orbit.auth.application.port.in.command.dto.RevokeAccountTokensCommand;

public interface RevokeAccountTokensUseCase {

    void revoke(RevokeAccountTokensCommand command);
}
