package com.orbit.notification.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.orbit.notification.domain.PushDevice;
import com.orbit.notification.domain.PushPlatform;

import io.swagger.v3.oas.annotations.media.Schema;

/** 앱 푸시 기기 등록 본문. */
public record PushDeviceRequest(
        @Schema(
                description = "기기 운영체제. IOS 또는 ANDROID",
                example = "ANDROID",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        PushPlatform platform,

        @Schema(
                description = "푸시 제공자(FCM)가 이 기기의 앱에 발급한 등록 토큰",
                example = "dGVzdC1mY20tdG9rZW46QVBBOTFiSGV4YW1wbGU",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = PushDevice.MAX_TOKEN_LENGTH)
        @NotBlank
        @Size(max = PushDevice.MAX_TOKEN_LENGTH)
        String token) {

    /** 요청을 로그에 남겨도 토큰이 찍히지 않게 한다. */
    @Override
    public String toString() {
        return "PushDeviceRequest[platform=" + platform + ", token=***]";
    }
}
