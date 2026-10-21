package com.orbit.notification.application.port.in.command.dto;

/** 요청 계정의 분류별 푸시 수신 여부. */
public record ChangeNotificationSettingCommand(
        Long accountId,
        boolean workAssignmentEnabled,
        boolean scheduleChangeEnabled,
        boolean workCancellationEnabled) {}
