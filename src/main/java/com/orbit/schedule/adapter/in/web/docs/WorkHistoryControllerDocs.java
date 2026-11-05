package com.orbit.schedule.adapter.in.web.docs;

import java.time.Instant;
import java.util.Set;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.history.WorkHistoryResponse;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.dto.WorkHistoryStatus;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.openapi.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Work history", description = "기사 작업 이력·통계 조회 API")
public interface WorkHistoryControllerDocs {

    @Operation(
            summary = "기사 작업 이력 조회",
            description = "기사 한 명의 작업 이력과 통계를 조회합니다. 구간 [from, to)와 그 기사의 배정 일정이 겹치는 작업을 담고, 최대 31일입니다. "
                    + "관리자는 technicianId로 조직의 어느 기사든 지정해야 하며, 지금 상태와 관계없이(다른 기사로 넘어갔거나 거절·취소된 작업 포함) 담습니다. "
                    + "기사는 technicianId를 비우거나 본인을 주며, 본인이 완료한 작업만 담고 통계도 그 작업으로만 셉니다(거절·취소 수 0). "
                    + "totalCompleted는 구간과 관계없이 그 기사가 지금 담당인 채 완료한 작업 수(누적 완료)입니다. "
                    + "workTypeIds(작업유형)·statuses(COMPLETED 완료, REJECTED 거절)를 주면 목록만 거르고 통계·누적 완료는 거르지 않습니다. "
                    + "작업유형 식별자가 양수가 아니면 SCHEDULE-003, 목록에 없는 상태 값은 COMMON-400입니다. "
                    + "관리자가 technicianId를 비웠거나, 식별자가 양수가 아니거나, 구간이 없거나 뒤집혔거나 31일을 넘으면 SCHEDULE-003, "
                    + "기사가 다른 기사를 지정하면 SCHEDULE-005, 요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, "
                    + "시각·식별자 형식 오류는 COMMON-400입니다. "
                    + "완료보고 내용은 작업마다 작업 상세 조회(works/{workId})의 completionReport로 엽니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = WorkHistoryResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = ScheduleErrorCode.class,
            includes = {"INVALID_WORK_INPUT", "NOT_ORGANIZATION_MEMBER", "ACTION_NOT_ALLOWED"})
    WorkHistoryResponse get(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "이력을 볼 기사 계약 식별자. 관리자는 필수, 기사는 비우거나 본인", example = "3") Long technicianId,
            @Parameter(description = "구간 시작(포함, ISO-8601 UTC)", example = "2026-09-30T15:00:00Z", required = true)
                    Instant from,
            @Parameter(description = "구간 끝(제외, ISO-8601 UTC)", example = "2026-10-31T15:00:00Z", required = true)
                    Instant to,
            @Parameter(description = "이 작업유형 중 하나인 작업만(반복하거나 쉼표로 이어 보냄)", example = "3") Set<Long> workTypeIds,
            @Parameter(description = "COMPLETED(그 기사가 담당인 채 완료)·REJECTED(그 기사가 거절) 중 하나인 작업만", example = "COMPLETED")
                    Set<WorkHistoryStatus> statuses);
}
