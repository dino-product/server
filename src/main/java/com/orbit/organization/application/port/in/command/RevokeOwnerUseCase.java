package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.RevokeOwnerCommand;

public interface RevokeOwnerUseCase {

    void revoke(RevokeOwnerCommand command);
}
