package com.orbit.notification.application.port.in.command.dto;

import com.orbit.notification.domain.PushPlatform;

/** 요청 계정에 등록할 앱 푸시 기기. 토큰은 문자열 표현에 남기지 않는다. */
public record RegisterPushDeviceCommand(Long accountId, PushPlatform platform, String token) {

    @Override
    public String toString() {
        return "RegisterPushDeviceCommand[accountId=" + accountId + ", platform=" + platform + ", token=***]";
    }
}
