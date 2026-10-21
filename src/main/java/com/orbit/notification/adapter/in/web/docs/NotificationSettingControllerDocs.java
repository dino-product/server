package com.orbit.notification.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.notification.adapter.in.web.NotificationSettingRequest;
import com.orbit.notification.adapter.in.web.NotificationSettingResponse;
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

@Tag(name = "Notification Settings", description = "알림 설정 API")
public interface NotificationSettingControllerDocs {

    @Operation(
            summary = "내 알림 설정 조회",
            description = "요청자 계정의 분류별 푸시 수신 여부를 돌려줍니다. 바꾼 적이 없으면 모두 켜짐입니다. 완료보고 요청 알림은 설정과 무관하게 " + "항상 푸시합니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NotificationSettingResponse.class)))
    @ApiErrorCodes(enumClass = CommonErrorCode.class, includes = "UNAUTHORIZED")
    NotificationSettingResponse get(@Parameter(hidden = true) AccountPrincipal requester);

    @Operation(
            summary = "내 알림 설정 저장",
            description = "요청자 계정의 분류별 푸시 수신 여부를 세 값 모두 받아 저장합니다. 토글을 바꿀 때마다 호출하며 다음 알림부터 적용됩니다. "
                    + "끈 분류도 알림센터에는 남습니다. 값이 빠졌거나 형식이 틀리면 COMMON-400, 같은 계정이 처음 저장하는 요청이 겹치면 "
                    + "NOTIFICATION-004(다시 보내면 저장)입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "저장 성공")
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(enumClass = NotificationErrorCode.class, includes = "NOTIFICATION_SETTING_BUSY")
    void change(@Parameter(hidden = true) AccountPrincipal requester, NotificationSettingRequest request);
}
