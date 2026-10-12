package com.orbit.profile.application.port.in.command;

import com.orbit.profile.application.port.in.command.dto.WithdrawCommand;

public interface WithdrawUseCase {

    void withdraw(WithdrawCommand command);
}
