package com.orbit.schedule.adapter.in.web.history;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.application.port.in.query.dto.WorkHistoryInfo;
import com.orbit.schedule.domain.AssignmentEndReason;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.WorkStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기사 작업 이력과 통계")
public record WorkHistoryResponse(
        @Schema(description = "조회한 기사 계약 식별자(기사가 비워 보냈으면 본인)", example = "3")
        Long technicianId,

        @Schema(description = "구간과 그 기사의 배정 일정이 겹친 작업. 마지막 배정 시작 시각 내림차순(최근 먼저), 작업 식별자 내림차순. 기사 본인이 조회하면 본인이 완료한 작업만")
        List<AssignedWork> works,

        @Schema(description = "구간의 작업 수 집계") Statistics statistics,

        @Schema(description = "구간과 관계없이 그 기사가 지금 담당인 채 완료한 작업 수(누적 완료)", example = "42")
        long totalCompleted) {

    static WorkHistoryResponse from(WorkHistoryInfo info) {
        return new WorkHistoryResponse(
                info.technicianId(),
                info.works().stream().map(AssignedWork::from).toList(),
                Statistics.from(info.statistics()),
                info.totalCompleted());
    }

    @Schema(description = "기사가 배정받았던 작업과 그 작업에서 기사의 마지막 배정(구간과 겹치는 것 중)")
    public record AssignedWork(
            @Schema(description = "작업 식별자", example = "10") Long workId,
            @Schema(description = "작업명", example = "보일러 점검") String name,

            @Schema(
                    description = "작업의 지금 상태(다른 기사로 넘어간 작업이면 그 뒤의 상태). REGISTERED(등록), PENDING_ACCEPT(배정됨), "
                            + "ACCEPTED(작업전), IN_PROGRESS(작업중), COMPLETED(완료), CANCELED(취소)",
                    example = "COMPLETED")
            WorkStatus status,

            @Schema(description = "이 배정이 작업의 마지막 배정이고 그 일정이 남아 있는지(완료·취소된 작업도 마지막 담당이면 true)", example = "true")
            boolean current,

            @Schema(description = "이 배정의 순번(1부터). current가 true일 때만 기사 요청에 쓴다", example = "1")
            int assignmentNumber,

            @Schema(description = "배정 일정 시작 시각", example = "2026-10-21T00:00:00Z")
            Instant startTime,

            @Schema(description = "배정 일정 종료 시각", example = "2026-10-21T02:00:00Z")
            Instant endTime,

            @Schema(
                    description = "배정 결과. PENDING(응답 전), ACCEPTED(수락), REJECTED(거절), WITHDRAWN(응답 전 회수)",
                    example = "ACCEPTED")
            AssignmentResult result,

            @Schema(
                    description = "관리자 조치나 탈퇴로 배정이 끝났으면 방식. REASSIGNED(재배정), RESCHEDULED(일정 변경), UNASSIGNED(배정 해제), "
                            + "CANCELLED(취소), ACCOUNT_WITHDRAWN(기사 탈퇴). 아니면 null",
                    example = "RESCHEDULED")
            AssignmentEndReason endReason) {

        static AssignedWork from(WorkHistoryInfo.AssignedWork info) {
            return new AssignedWork(
                    info.workId(),
                    info.name(),
                    info.status(),
                    info.current(),
                    info.assignmentNumber(),
                    info.startTime(),
                    info.endTime(),
                    info.result(),
                    info.endReason());
        }
    }

    @Schema(
            description = "구간의 작업 수 집계. assigned는 이력의 작업 수이고, completed·rejected·cancelled는 "
                    + "배정 시작 시각이 구간 안인 것만 센다(경계에 걸친 일정은 시작 쪽 구간)")
    public record Statistics(
            @Schema(description = "이력의 모든 작업 수", example = "12")
            int assigned,

            @Schema(description = "지금 담당인 채 완료된 작업 수", example = "10")
            int completed,

            @Schema(description = "거절한 작업 수(기사 본인 조회는 0)", example = "1")
            int rejected,

            @Schema(description = "지금 담당인 채 취소된 작업 수(기사 본인 조회는 0)", example = "1")
            int cancelled) {

        static Statistics from(WorkHistoryInfo.Statistics info) {
            return new Statistics(info.assigned(), info.completed(), info.rejected(), info.cancelled());
        }
    }
}
