package com.orbit.schedule.adapter.in.web.docs;

import java.time.Instant;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.myschedule.MyScheduleResponse;
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

@Tag(name = "My schedule", description = "기사 앱의 내 일정·응답 필요·지금 수행 가능 작업 조회 API")
public interface MyScheduleControllerDocs {

    @Operation(
            summary = "내 일정 조회",
            description = "기사 본인의 일정을 조회합니다. 구간 [from, to)와 일정이 겹치는, 지금 본인이 담당인 작업(배정됨·작업전·작업중·완료)을 "
                    + "시작 시각 순으로 돌려줍니다. 화면의 날짜·주를 조직의 현지 시간대로 바꾼 UTC 구간을 보내며 최대 31일입니다. "
                    + "다른 기사로 바뀌었거나 대기함으로 돌아간 작업은 담지 않습니다. "
                    + "구간이 없거나 뒤집혔거나 31일을 넘거나 조직 식별자가 양수가 아니면 SCHEDULE-003, 시각 형식 오류는 COMMON-400, "
                    + "요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 관리자면 SCHEDULE-005입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MyScheduleResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = ScheduleErrorCode.class,
            includes = {"INVALID_WORK_INPUT", "NOT_ORGANIZATION_MEMBER", "ACTION_NOT_ALLOWED"})
    MyScheduleResponse get(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "구간 시작(포함, ISO-8601 UTC)", example = "2026-10-20T15:00:00Z", required = true)
                    Instant from,
            @Parameter(description = "구간 끝(제외, ISO-8601 UTC)", example = "2026-10-27T15:00:00Z", required = true)
                    Instant to);

    @Operation(
            summary = "응답 필요 작업 조회",
            description = "날짜와 관계없이 지금 본인에게 배정돼 수락·거절을 기다리는 작업(배정됨)을 시작 시각 순으로 모두 돌려줍니다. "
                    + "응답 필요 건수는 목록 길이입니다. 조직 식별자가 양수가 아니면 SCHEDULE-003, "
                    + "요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 관리자면 SCHEDULE-005입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MyScheduleResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = ScheduleErrorCode.class,
            includes = {"INVALID_WORK_INPUT", "NOT_ORGANIZATION_MEMBER", "ACTION_NOT_ALLOWED"})
    MyScheduleResponse pending(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId);

    @Operation(
            summary = "지금 수행 가능한 작업 조회",
            description = "조회 시각이 시작 가능 시간(예정 시작 10분 전부터 예정 종료까지) 안인 작업전 작업과, 언제 시작했든 작업중인 작업을 "
                    + "시작 시각 순으로 돌려줍니다. 목록의 작업전 작업은 작업 시작 API로 바로 시작할 수 있습니다. "
                    + "조직 식별자가 양수가 아니면 SCHEDULE-003, 요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 관리자면 SCHEDULE-005입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MyScheduleResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = ScheduleErrorCode.class,
            includes = {"INVALID_WORK_INPUT", "NOT_ORGANIZATION_MEMBER", "ACTION_NOT_ALLOWED"})
    MyScheduleResponse actionable(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId);
}
