package com.orbit.schedule.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.CancelWorkRequest;
import com.orbit.schedule.adapter.in.web.CreateWorkRequest;
import com.orbit.schedule.adapter.in.web.CreatedWorkResponse;
import com.orbit.schedule.adapter.in.web.UpdateWorkDetailsRequest;
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

@Tag(name = "Works", description = "작업 등록·기본정보 수정·취소 API")
public interface WorkControllerDocs {

    @Operation(
            summary = "작업 등록",
            description = "작업을 등록합니다. 작업명만 필수입니다. schedule(담당기사·시작 시각·예상 소요시간)을 모두 주면 등록과 함께 배정해 "
                    + "기사의 수락을 기다리고, 비우면 대기함에 둡니다. schedule 항목을 일부만 주면 입력한 값을 남긴 채 대기함에 둡니다. "
                    + "같은 기사의 다른 작업과 시간이 겹치는데 schedule.conflictConfirmed가 false면 작업을 만들지 않고 applied=false와 "
                    + "겹친 작업을 돌려줍니다(오류 아님). 확인 뒤 schedule.conflictConfirmed=true로 다시 보내면 만듭니다. "
                    + "입력 규칙 위반은 SCHEDULE-003, 본문 형식 오류(목록에 없는 결제 방식 등)는 COMMON-400, 선택할 수 없는 작업 유형은 SCHEDULE-014, "
                    + "배정하거나 일부만 준 담당기사가 그 조직의 활성 기사 계약이 아니면 SCHEDULE-013, 같은 기사의 다른 변경을 기다리다 시간이 지나면 SCHEDULE-006입니다. "
                    + "요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 기사면 SCHEDULE-005입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "등록했거나 겹침 경고로 등록을 보류함",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreatedWorkResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED", "NOT_FOUND"})
    @ApiErrorCodes(
            enumClass = ScheduleErrorCode.class,
            includes = {
                "INVALID_WORK_INPUT",
                "WORK_TYPE_NOT_SELECTABLE",
                "TECHNICIAN_NOT_ASSIGNABLE",
                "NOT_ORGANIZATION_MEMBER",
                "ACTION_NOT_ALLOWED",
                "TECHNICIAN_SCHEDULE_BUSY"
            })
    CreatedWorkResponse create(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            CreateWorkRequest request);

    @Operation(
            summary = "작업 기본정보 수정",
            description = "작업명·작업 유형·고객·결제 정보를 한꺼번에 교체합니다. 비운 선택 항목은 지우고 상태·배정은 그대로 둡니다. "
                    + "완료·취소된 작업은 SCHEDULE-002입니다. 입력 규칙 위반은 SCHEDULE-003, 본문 형식 오류(목록에 없는 결제 방식 등)는 COMMON-400, "
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
                    + "목록에 없는 사유 등 본문 형식 오류는 COMMON-400입니다. 완료됐거나 이미 취소된 작업은 SCHEDULE-002입니다. "
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
