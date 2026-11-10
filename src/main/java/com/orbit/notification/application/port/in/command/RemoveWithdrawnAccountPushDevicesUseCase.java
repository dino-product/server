package com.orbit.notification.application.port.in.command;

import com.orbit.notification.application.port.in.command.dto.RemoveWithdrawnAccountPushDevicesCommand;

public interface RemoveWithdrawnAccountPushDevicesUseCase {

    void remove(RemoveWithdrawnAccountPushDevicesCommand command);
}
