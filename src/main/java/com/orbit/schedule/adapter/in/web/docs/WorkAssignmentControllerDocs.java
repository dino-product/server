package com.orbit.schedule.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.ScheduleChangeResponse;
import com.orbit.schedule.adapter.in.web.WorkScheduleRequest;
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

@Tag(name = "Work assignments", description = "작업 배정 변경·해제 API")
public interface WorkAssignmentControllerDocs {

    @Operation(
            summary = "배정 변경",
            description = "타임테이블에서 작업을 놓거나 옮긴 결과(담당기사·시작 시각·예상 소요시간)를 반영합니다. "
                    + "대기함 작업이면 배정, 지금 담당기사와 같은 기사면 일정 변경, 다른 기사면 재배정으로 처리하고 "
                    + "배정·재배정은 기사에게 다시 수락을 받습니다. "
                    + "같은 기사의 다른 작업과 시간이 겹치는데 conflictConfirmed가 false면 반영하지 않고 applied=false와 겹친 작업을 돌려줍니다(오류 아님). "
                    + "사용자가 확인하면 conflictConfirmed=true로 다시 보내 반영합니다. "
                    + "같은 기사·같은 시간이면 SCHEDULE-008, 배정·재배정할 기사가 그 조직의 활성 기사 계약이 아니면 SCHEDULE-013, "
                    + "같은 기사의 다른 변경을 기다리다 시간이 지나면 SCHEDULE-006입니다. "
                    + "대기함·수락대기·수락됨이 아닌 작업은 SCHEDULE-002, 입력 규칙 위반은 SCHEDULE-003입니다. "
                    + "요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 기사면 SCHEDULE-005, "
                    + "작업이 없거나 다른 조직의 작업이면 SCHEDULE-001입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "반영했거나 겹침 경고로 반영을 보류함",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ScheduleChangeResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED", "NOT_FOUND"})
    @ApiErrorCodes(
            enumClass = ScheduleErrorCode.class,
            includes = {
                "INVALID_WORK_INPUT",
                "TECHNICIAN_NOT_ASSIGNABLE",
                "NOT_ORGANIZATION_MEMBER",
                "ACTION_NOT_ALLOWED",
                "WORK_NOT_FOUND",
                "INVALID_WORK_STATE",
                "SCHEDULE_UNCHANGED",
                "TECHNICIAN_SCHEDULE_BUSY"
            })
    ScheduleChangeResponse change(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "작업 식별자", example = "10", required = true) Long workId,
            WorkScheduleRequest request);

    @Operation(
            summary = "배정 해제",
            description = "배정된 작업의 담당기사·일정을 풀어 대기함으로 돌려보냅니다. 현재 배정은 해제로 끝나고 이력은 남습니다. "
                    + "수락대기·수락됨이 아닌 작업(대기함·작업중·완료·취소)은 SCHEDULE-002입니다. "
                    + "요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 기사면 SCHEDULE-005, "
                    + "작업이 없거나 다른 조직의 작업이면 SCHEDULE-001입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "해제 성공")
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
    void unassign(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "작업 식별자", example = "10", required = true) Long workId);
}
