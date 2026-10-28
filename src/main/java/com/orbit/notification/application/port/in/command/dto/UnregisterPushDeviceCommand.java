package com.orbit.notification.application.port.in.command.dto;

/** 요청 계정이 해제할 앱 푸시 기기. */
public record UnregisterPushDeviceCommand(Long accountId, Long pushDeviceId) {}
