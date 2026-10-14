package com.orbit.schedule.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.CancelWorkRequest;
import com.orbit.schedule.adapter.in.web.UpdateWorkDetailsRequest;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.openapi.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Works", description = "작업 등록·기본정보 수정·취소 API")
public interface WorkControllerDocs {

    @Operation(
            summary = "작업 기본정보 수정",
            description = "작업명·작업 유형·고객·결제 정보를 한꺼번에 교체합니다. 비운 선택 항목은 지우고 상태·배정은 그대로 둡니다. "
                    + "완료·취소된 작업은 SCHEDULE-002입니다. 입력 규칙 위반은 SCHEDULE-003, "
                    + "그 조직에서 선택할 수 없는 작업 유형은 SCHEDULE-014입니다. "
                    + "요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 기사면 SCHEDULE-005, "
                    + "작업이 없거나 다른 조직의 작업이면 SCHEDULE-001입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "수정 성공")
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED", "NOT_FOUND"})
    @ApiErrorCodes(
            enumClass = ScheduleErrorCode.class,
            includes = {
                "INVALID_WORK_INPUT",
                "WORK_TYPE_NOT_SELECTABLE",
                "NOT_ORGANIZATION_MEMBER",
                "ACTION_NOT_ALLOWED",
                "WORK_NOT_FOUND",
                "INVALID_WORK_STATE"
            })
    void updateDetails(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "작업 식별자", example = "10", required = true) Long workId,
            UpdateWorkDetailsRequest request);

    @Operation(
            summary = "작업 취소",
            description = "완료 전 작업을 취소하고 취소 시각·처리자·사유를 남깁니다. 배정이 있으면 그 배정도 취소로 끝내며 되돌릴 수 없습니다. "
                    + "사유가 없거나, 기타(OTHER)인데 메모가 없거나 255자를 넘거나, 기타가 아닌데 메모가 있으면 SCHEDULE-003입니다. "
                    + "완료됐거나 이미 취소된 작업은 SCHEDULE-002입니다. "
                    + "요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 기사면 SCHEDULE-005, "
                    + "작업이 없거나 다른 조직의 작업이면 SCHEDULE-001입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "취소 성공")
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED", "NOT_FOUND"})
    @ApiErrorCodes(
            enumClass = ScheduleErrorCode.class,
            includes = {
                "INVALID_WORK_INPUT",
                "NOT_ORGANIZATION_MEMBER",
                "ACTION_NOT_ALLOWED",
                "WORK_NOT_FOUND",
                "INVALID_WORK_STATE"
            })
    void cancel(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "작업 식별자", example = "10", required = true) Long workId,
            CancelWorkRequest request);
}
