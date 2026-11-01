package com.orbit.notification.application.port.in.command;

import com.orbit.notification.application.port.in.command.dto.UnregisterPushDeviceCommand;

public interface UnregisterPushDeviceUseCase {

    void unregister(UnregisterPushDeviceCommand command);
}
