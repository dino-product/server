package com.orbit.profile.application.port.in.command;

import com.orbit.profile.application.port.in.command.dto.ChangeMarketingConsentCommand;

public interface ChangeMarketingConsentUseCase {

    void change(ChangeMarketingConsentCommand command);
}
