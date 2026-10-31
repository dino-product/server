package com.orbit.schedule.adapter.in.web.docs;

import java.time.Instant;

import jakarta.servlet.http.HttpServletResponse;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.board.ProgressBoardResponse;
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

@Tag(name = "Progress Board", description = "진행 보드 조회 API")
public interface ProgressBoardControllerDocs {

    @Operation(
            summary = "진행 보드 조회",
            description = "관리자가 구간 [from, to)와 보일 일정이 겹치는 작업을 등록·배정됨·작업전·작업중·완료 열로 나눠 조회합니다. 등록 열은 "
                    + "등록 때 미리 입력한 시간이나 배정됐다 돌아온 작업의 마지막 배정 시간이 구간에 든 대기함 작업이며(시간을 모르면 담지 않음), "
                    + "거절로 돌아온 작업은 returnedByRejection으로 표시합니다. 취소된 작업은 담지 않습니다. "
                    + "화면은 오늘 하루를 조직의 현지 시간대로 바꾼 UTC 구간으로 보내며(최대 31일까지 받음), 일정이 그 구간과 겹치는 "
                    + "작업만 담으므로 이전 날짜에 끝나지 않은 지연 작업은 이어서 보이지 않습니다. 요약의 진행 중은 작업중 작업만 셉니다. "
                    + "새로고침 없는 갱신은 실시간 갱신 연결(GET /api/v1/organizations/{organizationId}/events)의 work-changed를 받아 "
                    + "이 API를 다시 불러 맞춥니다. "
                    + "구간이 없거나 뒤집혔거나 31일을 넘거나 조직 식별자가 양수가 아니면 SCHEDULE-003, 시각 형식 오류는 COMMON-400입니다. "
                    + "요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 기사면 SCHEDULE-005입니다. "
                    + "서버가 동시에 처리하는 조회 수를 넘었거나 DB 커넥션을 제때 얻지 못하면 COMMON-503(503)과 Retry-After(초)를 "
                    + "돌려주므로, 화면은 그만큼 기다렸다가(여러 화면이 겹치지 않게 조금씩 흩어) 다시 조회합니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProgressBoardResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED", "SERVICE_UNAVAILABLE"})
    @ApiErrorCodes(
            enumClass = ScheduleErrorCode.class,
            includes = {"INVALID_WORK_INPUT", "NOT_ORGANIZATION_MEMBER", "ACTION_NOT_ALLOWED"})
    ProgressBoardResponse get(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "구간 시작(포함, ISO-8601 UTC)", example = "2026-12-17T15:00:00Z", required = true)
                    Instant from,
            @Parameter(description = "구간 끝(제외, ISO-8601 UTC)", example = "2026-12-18T15:00:00Z", required = true)
                    Instant to,
            @Parameter(hidden = true) HttpServletResponse response);
}
