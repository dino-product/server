package com.orbit.schedule.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.field.AssignmentNumberRequest;
import com.orbit.schedule.adapter.in.web.field.RejectWorkRequest;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.openapi.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Technician works", description = "기사 앱의 배정 응답·작업 시작 API")
public interface TechnicianWorkControllerDocs {

    @Operation(
            summary = "배정 수락",
            description = "담당 기사 본인이 화면에서 본 배정(assignmentNumber)을 수락해 작업전으로 바꾸고 사무실에 알립니다. "
                    + "이미 수락한 배정이 그대로면(작업전·작업중·완료) 다시 보내도 아무것도 바꾸지 않고 204입니다(중복 전송). "
                    + "배정 순번이 없거나 1보다 작으면 SCHEDULE-003, 본문이 없거나 형식이 틀리면 COMMON-400입니다. "
                    + "그 순번의 배정이 없거나 본인 배정이 아니면 SCHEDULE-009, "
                    + "그 뒤로 일정이 바뀌었거나 다른 기사·대기함을 거쳐 다시 배정됐으면 SCHEDULE-010, 취소된 작업이면 SCHEDULE-002입니다. "
                    + "요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 관리자면 SCHEDULE-005, "
                    + "작업이 없거나 다른 조직의 작업이거나 본인이 볼 수 없는 작업(거절함·다른 기사로 재배정됨·배정 해제·반환됨)이면 SCHEDULE-001, "
                    + "조직·작업 식별자가 양수가 아니면 SCHEDULE-003, 숫자가 아니면 COMMON-400입니다. "
                    + "같은 기사의 다른 요청을 기다리다 대기 한도를 넘기면 SCHEDULE-006입니다. "
                    + "같은 작업을 동시에 바꾼 다른 요청과 저장이 겹치면 서버가 최신 상태로 한 번 다시 처리하고, 그래도 겹치면 SCHEDULE-016(다시 조회 뒤 재시도)입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "수락함(이미 수락한 배정 포함)")
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
                "TECHNICIAN_SCHEDULE_BUSY",
                "WORK_CHANGED"
            })
    void accept(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "작업 식별자", example = "10", required = true) Long workId,
            AssignmentNumberRequest request);

    @Operation(
            summary = "배정 거절",
            description = "담당 기사 본인이 화면에서 본 배정(assignmentNumber)을 사유와 함께 거절합니다. 작업은 대기함으로 돌아가고 사무실에 알립니다. "
                    + "거절한 작업은 그 기사에게 보이지 않으므로, 거절을 다시 보내면 SCHEDULE-001입니다. "
                    + "배정 순번이나 사유가 없거나, 기타(OTHER)인데 메모가 없거나 255자를 넘거나, 기타가 아닌데 메모가 있으면 SCHEDULE-003, "
                    + "목록에 없는 사유 등 본문 형식 오류는 COMMON-400입니다. "
                    + "그 순번의 배정이 없거나 본인 배정이 아니면 SCHEDULE-009, "
                    + "그 뒤로 일정이 바뀌었거나 다른 기사·대기함을 거쳐 다시 배정됐으면 SCHEDULE-010, "
                    + "배정됨이 아니면(수락함·취소 등) SCHEDULE-002입니다. "
                    + "요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 관리자면 SCHEDULE-005, "
                    + "작업이 없거나 다른 조직의 작업이거나 본인이 볼 수 없는 작업이면 SCHEDULE-001, "
                    + "조직·작업 식별자가 양수가 아니면 SCHEDULE-003, 숫자가 아니면 COMMON-400입니다. "
                    + "같은 기사의 다른 요청을 기다리다 대기 한도를 넘기면 SCHEDULE-006입니다. "
                    + "같은 작업을 동시에 바꾼 다른 요청과 저장이 겹치면 서버가 최신 상태로 한 번 다시 처리하고, 그래도 겹치면 SCHEDULE-016(다시 조회 뒤 재시도)입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "거절함")
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
                "TECHNICIAN_SCHEDULE_BUSY",
                "WORK_CHANGED"
            })
    void reject(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "작업 식별자", example = "10", required = true) Long workId,
            RejectWorkRequest request);

    @Operation(
            summary = "작업 시작",
            description = "담당 기사 본인이 화면에서 본 배정(assignmentNumber)의 작업전 작업을 작업중으로 바꾸고 지금 시각을 시작 시각으로 남깁니다. "
                    + "예정 시작 10분 전부터 예정 종료 시각까지만 시작할 수 있어, 이르면 SCHEDULE-017, 예정 종료가 지났으면 SCHEDULE-018입니다"
                    + "(사무실이 일정을 바꾸면 다시 시작할 수 있음). 같은 기사의 다른 작업이 작업중이어도 시작할 수 있습니다. "
                    + "배정이 그대로인 채 이미 시작한 작업(완료 포함)에 다시 보내면 아무것도 바꾸지 않고 204입니다(중복 전송). "
                    + "배정 순번이 없거나 1보다 작으면 SCHEDULE-003, 본문이 없거나 형식이 틀리면 COMMON-400입니다. "
                    + "그 순번의 배정이 없거나 본인 배정이 아니면 SCHEDULE-009, "
                    + "그 뒤로 일정이 바뀌었거나 다른 기사·대기함을 거쳐 다시 배정됐으면 SCHEDULE-010, "
                    + "작업전이 아니면(수락 전·취소) SCHEDULE-002입니다. "
                    + "요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 관리자면 SCHEDULE-005, "
                    + "작업이 없거나 다른 조직의 작업이거나 본인이 볼 수 없는 작업이면 SCHEDULE-001, "
                    + "조직·작업 식별자가 양수가 아니면 SCHEDULE-003, 숫자가 아니면 COMMON-400입니다. "
                    + "같은 기사의 다른 요청을 기다리다 대기 한도를 넘기면 SCHEDULE-006입니다. "
                    + "같은 작업을 동시에 바꾼 다른 요청과 저장이 겹치면 서버가 최신 상태로 한 번 다시 처리하고, 그래도 겹치면 SCHEDULE-016(다시 조회 뒤 재시도)입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "시작함(이미 시작한 작업 포함)")
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
                "TOO_EARLY_TO_START",
                "START_TIME_PASSED",
                "TECHNICIAN_SCHEDULE_BUSY",
                "WORK_CHANGED"
            })
    void start(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "작업 식별자", example = "10", required = true) Long workId,
            AssignmentNumberRequest request);
}
