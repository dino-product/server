package com.orbit.notification.application.port.in.command;

import com.orbit.notification.application.port.in.command.dto.ChangeNotificationSettingCommand;

public interface ChangeNotificationSettingUseCase {

    void change(ChangeNotificationSettingCommand command);
}
