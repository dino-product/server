package com.orbit.auth.application.port.in.command;

import com.orbit.auth.application.port.in.command.dto.UnlinkAccountProvidersCommand;

public interface UnlinkAccountProvidersUseCase {

    void unlink(UnlinkAccountProvidersCommand command);
}
