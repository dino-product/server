package com.orbit.notification.application.port.in.command;

import com.orbit.notification.application.port.in.command.dto.RegisterPushDeviceCommand;
import com.orbit.notification.application.port.in.command.dto.RegisteredPushDeviceInfo;

public interface RegisterPushDeviceUseCase {

    RegisteredPushDeviceInfo register(RegisterPushDeviceCommand command);
}
