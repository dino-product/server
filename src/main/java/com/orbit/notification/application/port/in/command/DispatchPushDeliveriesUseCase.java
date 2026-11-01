package com.orbit.notification.application.port.in.command;

import com.orbit.notification.application.port.in.command.dto.DispatchPushDeliveriesCommand;

/** 보낼 때가 된 기사 앱 푸시 발신 대기를 집어 보내고 결과를 기록한다. 주기 작업이 부른다. */
public interface DispatchPushDeliveriesUseCase {

    void dispatch(DispatchPushDeliveriesCommand command);
}
