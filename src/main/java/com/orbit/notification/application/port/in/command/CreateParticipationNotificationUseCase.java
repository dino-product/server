package com.orbit.notification.application.port.in.command;

import com.orbit.notification.application.port.in.command.dto.CreateParticipationNotificationCommand;

/** 참여 요청의 승인·거절 결과를 요청자의 알림으로 남기고 푸시한다. */
public interface CreateParticipationNotificationUseCase {

    void create(CreateParticipationNotificationCommand command);
}
