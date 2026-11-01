package com.orbit.notification.adapter.in.web;

import com.orbit.notification.application.port.in.command.dto.RegisteredPushDeviceInfo;

import io.swagger.v3.oas.annotations.media.Schema;

public record RegisteredPushDeviceResponse(
        @Schema(description = "등록된 기기 식별자. 로그아웃할 때 이 값으로 기기를 해제한다. 같은 토큰을 다시 등록하면 같은 값이다", example = "1")
        Long pushDeviceId) {

    static RegisteredPushDeviceResponse from(RegisteredPushDeviceInfo info) {
        return new RegisteredPushDeviceResponse(info.pushDeviceId());
    }
}
