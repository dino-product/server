package com.orbit.notification.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.notification.adapter.in.web.PushDeviceRequest;
import com.orbit.notification.adapter.in.web.RegisteredPushDeviceResponse;
import com.orbit.notification.application.error.NotificationErrorCode;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.openapi.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Push Devices", description = "앱 푸시 기기 API")
public interface PushDeviceControllerDocs {

    @Operation(
            summary = "앱 푸시 기기 등록",
            description = "요청자 계정으로 앱 푸시를 받을 기기를 등록합니다. 앱을 시작할 때와 토큰이 바뀔 때마다 호출합니다. 같은 토큰을 다시 등록하면 "
                    + "같은 기기 식별자를 돌려주고, 다른 계정이 등록했던 토큰이면 기기가 요청자 계정으로 넘어갑니다. 값이 빠졌거나 형식이 틀리면 "
                    + "COMMON-400입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "등록 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RegisteredPushDeviceResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    RegisteredPushDeviceResponse register(
            @Parameter(hidden = true) AccountPrincipal requester, PushDeviceRequest request);

    @Operation(
            summary = "앱 푸시 기기 해제",
            description = "요청자 계정의 기기를 해제해 그 기기로 이 계정의 앱 푸시가 더 가지 않게 합니다. 로그아웃하기 직전에 등록 때 받은 기기 "
                    + "식별자로 호출합니다. 없거나 다른 계정의 기기면 NOTIFICATION-005입니다. 이미 해제했거나 같은 기기에서 다른 계정이 등록해 "
                    + "기기가 넘어간 경우도 같습니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "해제 성공")
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(enumClass = NotificationErrorCode.class, includes = "PUSH_DEVICE_NOT_FOUND")
    void unregister(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "등록 때 받은 기기 식별자", example = "1", required = true) Long pushDeviceId);
}
