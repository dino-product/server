package com.orbit.notification.application.port.in.command.dto;

/** 요청 계정이 받은 알림 한 건을 읽음으로 바꾼다. */
public record ReadNotificationCommand(Long accountId, Long notificationId) {}
