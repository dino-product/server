package com.orbit.notification.application.port.in.command;

import com.orbit.notification.application.port.in.command.dto.CreateWorkNotificationCommand;

/** 작업에 일어난 일을 받는 사람의 알림으로 남기고, 푸시 대상이면 푸시한다. */
public interface CreateWorkNotificationUseCase {

    void create(CreateWorkNotificationCommand command);
}
