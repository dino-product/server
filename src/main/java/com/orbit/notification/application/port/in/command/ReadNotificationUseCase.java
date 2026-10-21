package com.orbit.notification.application.port.in.command;

import com.orbit.notification.application.port.in.command.dto.ReadNotificationCommand;

public interface ReadNotificationUseCase {

    void read(ReadNotificationCommand command);
}
