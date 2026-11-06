package com.orbit.schedule.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.field.SubmitCompletionReportRequest;
import com.orbit.schedule.adapter.in.web.field.WorkCompletionReportResponse;
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

@Tag(name = "Completion report", description = "완료보고 제출·조회 API")
public interface CompletionReportControllerDocs {

    @Operation(
            summary = "완료보고 제출",
            description = "담당 기사 본인이 화면에서 본 배정(assignmentNumber)의 작업중 작업에 완료보고를 붙여 완료로 바꾸고, 지금 시각을 완료 시각으로 "
                    + "남기며 사무실에 알립니다. 배정 순번 말고는 모두 비워 보낼 수 있습니다. 사진은 완료보고 사진 업로드 주소"
                    + "(works/{workId}/completion-photo-uploads)로 먼저 올린 본인 파일의 식별자로 작업 전·후 각각 최대 6장입니다. "
                    + "같은 보고로 이미 완료한 작업에 다시 보내면 204(중복 전송)이고, 다른 보고면 SCHEDULE-002입니다(제출한 보고는 고칠 수 없음). "
                    + "배정 순번이 없거나 1보다 작거나, 사진이 7장 이상이거나 같은 사진이 겹치거나, 메모·부품이 255자를 넘거나, 금액이 음수면 SCHEDULE-003, "
                    + "목록에 없는 결제 방식 등 본문 형식 오류는 COMMON-400입니다. "
                    + "이미지가 아닌 사진은 SCHEDULE-020, 업로드 때 밝힌 크기보다 크면 SCHEDULE-021, "
                    + "업로드를 마치지 않았거나 본인이 올리지 않았거나 다른 작업·현장 사진용인 사진은 "
                    + "SCHEDULE-022, 사진 저장소에 닿지 못하면 SCHEDULE-023(503, 사진을 보낸 요청은 다른 확인보다 먼저 날 수 있음)입니다. "
                    + "그 순번의 배정이 없거나 본인 배정이 아니면 SCHEDULE-009, 그 뒤로 배정이 바뀌었으면 SCHEDULE-010, "
                    + "작업중이 아니면(작업전·취소 등) SCHEDULE-002입니다. "
                    + "요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 관리자면 SCHEDULE-005, "
                    + "작업이 없거나 다른 조직의 작업이거나 본인이 볼 수 없는 작업이면 SCHEDULE-001, "
                    + "조직·작업 식별자가 양수가 아니면 SCHEDULE-003, 숫자가 아니면 COMMON-400입니다. "
                    + "같은 작업을 동시에 바꾼 다른 요청과 저장이 겹치면 서버가 최신 상태로 한 번 다시 처리하고, 그래도 겹치면 SCHEDULE-016(다시 조회 뒤 재시도)입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "제출함(같은 보고의 중복 전송 포함)")
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
                "NOT_ASSIGNED_TECHNICIAN",
                "ASSIGNMENT_CHANGED",
                "INVALID_WORK_STATE",
                "PHOTO_TYPE_NOT_ALLOWED",
                "PHOTO_TOO_LARGE",
                "PHOTO_NOT_UPLOADED",
                "PHOTO_STORAGE_UNAVAILABLE",
                "WORK_CHANGED"
            })
    void submit(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "작업 식별자", example = "10", required = true) Long workId,
            SubmitCompletionReportRequest request);

    @Operation(
            summary = "완료보고 조회",
            description = "완료한 작업의 완료보고(사진 내려받기 주소 포함)를 조회합니다. 관리자는 조직의 모든 작업을, 기사는 본인이 볼 수 있는 작업"
                    + "(지금 담당인 작업)만 봅니다. 기사의 내 작업 이력에서 완료보고를 다시 열 때 씁니다. "
                    + "완료 전이라 보고가 없으면 SCHEDULE-012, 작업이 없거나 다른 조직의 작업이거나 기사가 볼 수 없는 작업이면 SCHEDULE-001, "
                    + "요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 조직·작업 식별자가 양수가 아니면 SCHEDULE-003, 숫자가 아니면 COMMON-400, "
                    + "사진 내려받기 주소를 서명하지 못하면 SCHEDULE-023(503)입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = WorkCompletionReportResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = ScheduleErrorCode.class,
            includes = {
                "INVALID_WORK_INPUT",
                "NOT_ORGANIZATION_MEMBER",
                "WORK_NOT_FOUND",
                "COMPLETION_REPORT_NOT_FOUND",
                "PHOTO_STORAGE_UNAVAILABLE"
            })
    WorkCompletionReportResponse get(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "작업 식별자", example = "10", required = true) Long workId);
}
