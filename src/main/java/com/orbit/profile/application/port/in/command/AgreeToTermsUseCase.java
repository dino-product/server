package com.orbit.profile.application.port.in.command;

import com.orbit.profile.application.port.in.command.dto.AgreeToTermsCommand;

public interface AgreeToTermsUseCase {

    void agree(AgreeToTermsCommand command);
}
