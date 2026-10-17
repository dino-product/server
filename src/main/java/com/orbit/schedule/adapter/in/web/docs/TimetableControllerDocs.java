package com.orbit.schedule.adapter.in.web.docs;

import java.time.Instant;
import java.util.Set;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.timetable.TimetableResponse;
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

@Tag(name = "Timetable", description = "타임테이블 조회 API")
public interface TimetableControllerDocs {

    @Operation(
            summary = "타임테이블 조회",
            description = "관리자가 조직의 타임테이블을 조회합니다. 활성 기사 행, 구간 [from, to)와 일정이 겹치는 작업 칸(배정됨·작업전·작업중·완료), "
                    + "대기함 작업 카드를 함께 돌려줍니다. 화면의 날짜를 조직의 현지 시간대로 바꾼 UTC 구간을 보내며 최대 31일입니다. "
                    + "technicianTypeIds·workTypeIds(반복하거나 쉼표로 이어 보냄)를 주면 기사 행과 작업 칸만 거르고 대기함은 거르지 않습니다. "
                    + "구간이 없거나 뒤집혔거나 31일을 넘거나, 유형·조직 식별자가 양수가 아니면 SCHEDULE-003, "
                    + "시각·식별자 형식 오류(숫자가 아닌 유형 식별자 등)는 COMMON-400입니다. "
                    + "요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 기사면 SCHEDULE-005입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TimetableResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = ScheduleErrorCode.class,
            includes = {"INVALID_WORK_INPUT", "NOT_ORGANIZATION_MEMBER", "ACTION_NOT_ALLOWED"})
    TimetableResponse get(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "구간 시작(포함, ISO-8601 UTC)", example = "2026-10-20T15:00:00Z", required = true)
                    Instant from,
            @Parameter(description = "구간 끝(제외, ISO-8601 UTC)", example = "2026-10-21T15:00:00Z", required = true)
                    Instant to,
            @Parameter(description = "이 기사유형 중 하나인 기사의 행과 작업 칸만", example = "7,8") Set<Long> technicianTypeIds,
            @Parameter(description = "이 작업유형 중 하나인 작업 칸만", example = "3") Set<Long> workTypeIds);
}
