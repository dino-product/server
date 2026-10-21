package com.orbit.schedule.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.CompletionReportRequestResponse;
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

@Tag(name = "Completion report requests", description = "완료보고 요청 API")
public interface CompletionReportRequestControllerDocs {

    @Operation(
            summary = "완료보고 요청",
            description = "예정 종료 시각이 지났는데 완료보고가 없는 작업중 작업의 담당 기사에게 완료보고를 요청합니다. "
                    + "요청 시각·요청자를 작업에 남기고 담당 기사에게 알림을 보냅니다(알림 설정과 관계없음). "
                    + "그 조직의 총관리자·직원 누구나 요청할 수 있습니다. "
                    + "작업중이 아니거나 예정 종료 시각 전이면 SCHEDULE-002, "
                    + "같은 작업의 마지막 요청 뒤 30분이 지나지 않았으면 SCHEDULE-019(429)이며 기록·알림을 남기지 않습니다. "
                    + "같은 작업에 동시에 들어온 요청이나 기사의 완료보고와 겹치면 SCHEDULE-016입니다. "
                    + "요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 기사면 SCHEDULE-005, "
                    + "작업이 없거나 다른 조직의 작업이면 SCHEDULE-001, 식별자가 양수가 아니면 SCHEDULE-003, 숫자가 아니면 COMMON-400입니다. "
                    + "작업 상세의 completionReportRequestable·completionReportRequests로 버튼과 요청 기록을 그립니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "요청함",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CompletionReportRequestResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = ScheduleErrorCode.class,
            includes = {
                "INVALID_WORK_INPUT",
                "NOT_ORGANIZATION_MEMBER",
                "ACTION_NOT_ALLOWED",
                "WORK_NOT_FOUND",
                "INVALID_WORK_STATE",
                "WORK_CHANGED",
                "COMPLETION_REPORT_REQUESTED_RECENTLY"
            })
    CompletionReportRequestResponse request(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "작업 식별자", example = "10", required = true) Long workId);
}
