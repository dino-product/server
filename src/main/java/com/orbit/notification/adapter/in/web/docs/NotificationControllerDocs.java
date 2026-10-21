package com.orbit.notification.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.notification.adapter.in.web.NotificationsResponse;
import com.orbit.notification.adapter.in.web.UnreadNotificationCountResponse;
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

@Tag(name = "Notifications", description = "알림센터 API")
public interface NotificationControllerDocs {

    @Operation(
            summary = "내 알림 목록 조회",
            description = "요청자 계정이 받은 알림을 최신순(고정)으로 쪽을 나눠 돌려줍니다. 여러 발주사의 알림을 계정 단위로 함께 보이며, "
                    + "각 알림의 organizationId로 발주사를 구분합니다. 기사가 거절한 작업의 이전 알림은 보이지 않습니다. "
                    + "쪽 입력이 범위를 벗어나면 NOTIFICATION-001, 숫자·불리언 형식 오류는 COMMON-400입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NotificationsResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(enumClass = NotificationErrorCode.class, includes = "INVALID_NOTIFICATION_INPUT")
    NotificationsResponse list(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "안 읽은 알림만(기본 false)", example = "false") boolean unreadOnly,
            @Parameter(description = "쪽 번호(0부터, 기본 0). page × size ≤ 10,000", example = "0") Integer page,
            @Parameter(description = "한 쪽의 알림 수(1~100, 기본 20)", example = "20") Integer size);

    @Operation(
            summary = "안 읽은 알림 수 조회",
            description = "헤더 알림 아이콘 배지에 보일, 요청자 계정이 받은 안 읽은 알림 수를 돌려줍니다. 목록에 보이지 않는 알림은 세지 않습니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UnreadNotificationCountResponse.class)))
    @ApiErrorCodes(enumClass = CommonErrorCode.class, includes = "UNAUTHORIZED")
    UnreadNotificationCountResponse countUnread(@Parameter(hidden = true) AccountPrincipal requester);

    @Operation(
            summary = "알림 읽음 처리",
            description = "요청자 계정이 받은 알림 한 건을 읽음으로 바꿉니다. 알림을 눌러 관련 화면으로 이동할 때 호출합니다. 이미 읽었으면 그대로 "
                    + "성공합니다. 다른 계정이 받은 알림이면 NOTIFICATION-003, 없거나 목록에 보이지 않는 알림이면 NOTIFICATION-002입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "읽음 처리 성공")
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = NotificationErrorCode.class,
            includes = {"NOT_NOTIFICATION_RECIPIENT", "NOTIFICATION_NOT_FOUND"})
    void read(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "알림 식별자", example = "31", required = true) Long notificationId);
}
