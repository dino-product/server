package com.orbit.schedule.adapter.in.web.search;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.adapter.in.web.LabelResponse;
import com.orbit.schedule.adapter.in.web.StaffResponse;
import com.orbit.schedule.adapter.in.web.TechnicianResponse;
import com.orbit.schedule.application.port.in.query.dto.WorkSearchInfo;
import com.orbit.schedule.domain.WorkStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "작업 목록 한 쪽")
public record WorkSearchResponse(
        @Schema(description = "요청한 정렬 순서의 작업. 쪽이 끝을 넘으면 비어 있다")
        List<WorkSummary> works,

        @Schema(description = "조건에 맞는 전체 작업 수", example = "15")
        long totalCount,

        @Schema(description = "적용한 쪽 번호(0부터)", example = "0")
        int page,

        @Schema(description = "적용한 한 쪽의 작업 수", example = "20")
        int size) {

    static WorkSearchResponse from(WorkSearchInfo info) {
        return new WorkSearchResponse(
                info.works().stream().map(WorkSummary::from).toList(), info.totalCount(), info.page(), info.size());
    }

    @Schema(description = "목록의 작업 한 줄")
    public record WorkSummary(
            @Schema(description = "작업 식별자", example = "10") Long workId,
            @Schema(description = "작업명", example = "배관 설치") String name,

            @Schema(
                    description = "작업 상태. REGISTERED(등록), PENDING_ACCEPTANCE(배정됨), ACCEPTED(작업전), "
                            + "IN_PROGRESS(작업중), COMPLETED(완료), CANCELLED(취소)",
                    example = "PENDING_ACCEPTANCE")
            WorkStatus status,

            @Schema(description = "작업에 남은 일정의 담당 기사. 일정이 없으면(대기함, 배정 전에 취소된 작업) null")
            TechnicianResponse technician,

            @Schema(description = "예정 시작 시각. 일정이 없으면 null", example = "2026-10-21T00:00:00Z")
            Instant startTime,

            @Schema(description = "예정 종료 시각. 일정이 없으면 null", example = "2026-10-21T02:00:00Z")
            Instant endTime,

            @Schema(description = "고객명. 없으면 null", example = "이정호")
            String customerName,

            @Schema(description = "작업유형. 없으면 null") LabelResponse workType,
            @Schema(description = "등록자") StaffResponse registrar,

            @Schema(description = "등록 시각", example = "2026-10-18T01:00:00Z")
            Instant registeredAt,

            @Schema(description = "최근 수정 시각(마지막 저장 시각)", example = "2026-10-19T03:00:00Z")
            Instant lastModifiedAt,

            @Schema(description = "조회 시각에 지연된 작업인지", example = "false")
            boolean delayed,

            @Schema(description = "기사가 거절해 대기함으로 돌아온 작업인지", example = "false")
            boolean returnedByRejection) {

        static WorkSummary from(WorkSearchInfo.WorkSummary info) {
            return new WorkSummary(
                    info.workId(),
                    info.name(),
                    info.status(),
                    TechnicianResponse.from(info.technician()),
                    info.startTime(),
                    info.endTime(),
                    info.customerName(),
                    LabelResponse.from(info.workType()),
                    StaffResponse.from(info.registrar()),
                    info.registeredAt(),
                    info.lastModifiedAt(),
                    info.delayed(),
                    info.returnedByRejection());
        }
    }
}
