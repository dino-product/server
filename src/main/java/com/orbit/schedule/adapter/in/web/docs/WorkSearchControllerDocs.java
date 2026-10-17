package com.orbit.schedule.adapter.in.web.docs;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;

import org.springframework.http.ResponseEntity;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.search.BacklogSummaryResponse;
import com.orbit.schedule.adapter.in.web.search.WorkSearchResponse;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.dto.WorkSearchFilter;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.openapi.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Work List", description = "작업 목록 조회 API")
public interface WorkSearchControllerDocs {

    @Operation(
            summary = "작업 목록 검색",
            description = "관리자가 조직의 작업을 조건으로 거르고 정렬해 쪽으로 나눠 조회합니다. 비운 조건은 거르지 않습니다. "
                    + "담당 기사·시작 구간은 작업에 남은 일정 기준이라 대기함 작업은 걸리지 않습니다. "
                    + "statuses는 반복하거나 쉼표로 이어 보냅니다(빈 원소가 섞이면 SCHEDULE-003). "
                    + "검색어가 100자를 넘거나, 식별자가 양수가 아니거나, 시작 구간을 한쪽만 주거나 뒤집었거나, 쪽 입력이 범위를 벗어나면 SCHEDULE-003, "
                    + "목록에 없는 상태·정렬이나 시각·숫자 형식 오류는 COMMON-400입니다. "
                    + "요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 기사면 SCHEDULE-005입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = WorkSearchResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = ScheduleErrorCode.class,
            includes = {"INVALID_WORK_INPUT", "NOT_ORGANIZATION_MEMBER", "ACTION_NOT_ALLOWED"})
    WorkSearchResponse search(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "작업명·고객 이름·연락처·주소에 포함되는 글자(최대 100자)", example = "보일러") String keyword,
            @Parameter(description = "이 상태 중 하나인 작업만", example = "PENDING_ACCEPTANCE") Set<WorkStatus> statuses,
            @Parameter(description = "작업에 남은 일정의 담당 기사(기사 계약 식별자)", example = "12") Long technicianId,
            @Parameter(description = "작업유형 식별자", example = "3") Long workTypeId,
            @Parameter(description = "등록자(직원·총관리자 소속 식별자)", example = "5") Long registrarId,
            @Parameter(description = "예정 시작 시각 하한(포함, ISO-8601 UTC). startTo와 함께", example = "2026-10-20T15:00:00Z")
                    Instant startFrom,
            @Parameter(description = "예정 시작 시각 상한(제외, ISO-8601 UTC)", example = "2026-10-21T15:00:00Z") Instant startTo,
            @Parameter(description = "기사가 거절해 대기함으로 돌아온 작업만", example = "false") boolean returnedByRejectionOnly,
            @Parameter(description = "지금 지연된 작업만", example = "false") boolean delayedOnly,
            @Parameter(
                            description = "정렬. REGISTERED_DESC(등록일 최신순, 기본), REGISTERED_ASC(등록일 오래된순), "
                                    + "START_TIME_ASC(예정시간 임박순, 일정 없는 작업은 맨 뒤), LAST_MODIFIED_DESC(최근 수정일순)",
                            example = "REGISTERED_DESC")
                    WorkSearchFilter.Sort sort,
            @Parameter(description = "쪽 번호(0부터, 기본 0)", example = "0") Integer page,
            @Parameter(description = "한 쪽의 작업 수(1~100, 기본 20)", example = "20") Integer size);

    @Operation(
            summary = "작업내역 출력",
            description = "작업 목록 검색과 같은 조건·정렬로 거른 결과 전체를 엑셀(xlsx) 파일로 내려받습니다. 열은 작업명·상태·담당기사·작업유형·"
                    + "예정시간·등록자·최근 수정일·고객명·금액·결제 방식이고, 시각은 timeZone(기본 Asia/Seoul)으로 바꿔 씁니다. "
                    + "금액·결제 방식은 등록 때 정한 값이고, 이름을 찾지 못한 기사·등록자는 '이름 없음'으로 씁니다. "
                    + "한 번에 10,000건까지 출력하며 넘으면 SCHEDULE-015입니다. 조건 오류는 작업 목록 검색과 같고(SCHEDULE-003, COMMON-400), "
                    + "알 수 없는 시간대는 COMMON-400입니다. 요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 기사면 SCHEDULE-005입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "출력 성공. 파일 이름은 작업내역_yyyyMMdd.xlsx",
            content =
                    @Content(
                            mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            schema = @Schema(type = "string", format = "binary")))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = ScheduleErrorCode.class,
            includes = {"INVALID_WORK_INPUT", "EXPORT_TOO_LARGE", "NOT_ORGANIZATION_MEMBER", "ACTION_NOT_ALLOWED"})
    ResponseEntity<byte[]> export(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "작업명·고객 이름·연락처·주소에 포함되는 글자(최대 100자)", example = "보일러") String keyword,
            @Parameter(description = "이 상태 중 하나인 작업만", example = "PENDING_ACCEPTANCE") Set<WorkStatus> statuses,
            @Parameter(description = "작업에 남은 일정의 담당 기사(기사 계약 식별자)", example = "12") Long technicianId,
            @Parameter(description = "작업유형 식별자", example = "3") Long workTypeId,
            @Parameter(description = "등록자(직원·총관리자 소속 식별자)", example = "5") Long registrarId,
            @Parameter(description = "예정 시작 시각 하한(포함, ISO-8601 UTC). startTo와 함께", example = "2026-10-20T15:00:00Z")
                    Instant startFrom,
            @Parameter(description = "예정 시작 시각 상한(제외, ISO-8601 UTC)", example = "2026-10-21T15:00:00Z") Instant startTo,
            @Parameter(description = "기사가 거절해 대기함으로 돌아온 작업만", example = "false") boolean returnedByRejectionOnly,
            @Parameter(description = "지금 지연된 작업만", example = "false") boolean delayedOnly,
            @Parameter(description = "정렬(작업 목록 검색과 같음)", example = "REGISTERED_DESC") WorkSearchFilter.Sort sort,
            @Parameter(
                            description = "파일에 쓸 시각의 시간대(IANA, 기본 Asia/Seoul)",
                            example = "Asia/Seoul",
                            schema = @Schema(type = "string", defaultValue = "Asia/Seoul"))
                    ZoneId timeZone);

    @Operation(
            summary = "대기함 요약 조회",
            description = "작업 목록 위에 고정하는 대기함 잔여·거절 작업을 조회합니다. 대기함 작업 수(totalCount, 거절로 돌아온 "
                    + "작업 포함)와 그 가운데 거절로 돌아온 작업을 뺀 수(registeredCount)·거절로 돌아온 작업 수(rejectedCount), "
                    + "오래된 순으로 쪽을 나눈 대기함 카드(부족 항목·등록자·거절 사유)를 돌려줍니다. "
                    + "쪽 입력이 범위를 벗어나면 SCHEDULE-003, 숫자 형식 오류는 COMMON-400입니다. "
                    + "요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 기사면 SCHEDULE-005입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BacklogSummaryResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = ScheduleErrorCode.class,
            includes = {"INVALID_WORK_INPUT", "NOT_ORGANIZATION_MEMBER", "ACTION_NOT_ALLOWED"})
    BacklogSummaryResponse backlog(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "쪽 번호(0부터, 기본 0)", example = "0") Integer page,
            @Parameter(description = "한 쪽의 작업 수(1~100, 기본 5)", example = "5") Integer size);
}
