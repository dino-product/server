package com.orbit.schedule.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.detail.WorkDetailResponse;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.openapi.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Work Detail", description = "작업 상세 조회 API")
public interface WorkDetailControllerDocs {

    @Operation(
            summary = "작업 상세 조회",
            description = "작업 하나의 상세를 조회합니다. 관리자는 조직의 모든 작업을 모든 항목과 함께 봅니다. 기사는 지금 자기에게 배정된 작업과 "
                    + "취소 당시 자기가 담당이던 취소 작업만 보며, 등록자와 완료보고 요청 기록·요청 가능 여부를 뺀 모든 항목과 지금 자기 배정 하나를 받습니다. "
                    + "상태 변경 이력은 등록 시각, 배정 이력의 배정·응답·종료 시각, 완료보고 요청 시각, 시작·완료·취소 시각으로 그립니다. "
                    + "요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 작업이 없거나 다른 조직의 작업이거나 기사가 볼 수 없는 작업"
                    + "(배정받은 적 없음, 거절함, 다른 기사로 재배정됨, 배정 해제·반환됨)이면 SCHEDULE-001, 식별자가 양수가 아니면 SCHEDULE-003, "
                    + "숫자가 아니면 COMMON-400입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = WorkDetailResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = ScheduleErrorCode.class,
            includes = {"INVALID_WORK_INPUT", "NOT_ORGANIZATION_MEMBER", "WORK_NOT_FOUND"})
    WorkDetailResponse get(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "작업 식별자", example = "10", required = true) Long workId);
}
