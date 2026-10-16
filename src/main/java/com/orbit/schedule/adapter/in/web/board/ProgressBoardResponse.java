package com.orbit.schedule.adapter.in.web.board;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.adapter.in.web.TechnicianResponse;
import com.orbit.schedule.application.port.in.query.dto.ProgressBoardInfo;
import com.orbit.schedule.domain.WorkStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "진행 보드")
public record ProgressBoardResponse(
        @Schema(description = "배정됨·작업전·작업중·완료 순서의 열. 작업이 없는 상태도 빈 열로 담는다")
        List<Column> columns,

        @Schema(description = "작업 수 집계") Summary summary) {

    static ProgressBoardResponse from(ProgressBoardInfo info) {
        return new ProgressBoardResponse(
                info.columns().stream().map(Column::from).toList(), Summary.from(info.summary()));
    }

    @Schema(description = "한 상태의 열")
    public record Column(
            @Schema(
                    description = "상태. PENDING_ACCEPTANCE(배정됨), ACCEPTED(작업전), IN_PROGRESS(작업중), COMPLETED(완료)",
                    example = "IN_PROGRESS")
            WorkStatus status,

            @Schema(description = "시작 시각·작업 식별자 순의 작업 카드") List<Card> cards) {

        static Column from(ProgressBoardInfo.Column info) {
            return new Column(
                    info.status(), info.cards().stream().map(Card::from).toList());
        }
    }

    @Schema(description = "작업 카드")
    public record Card(
            @Schema(description = "작업 식별자", example = "10") Long workId,
            @Schema(description = "작업명", example = "인덕션 수리") String name,
            @Schema(description = "담당 기사") TechnicianResponse technician,

            @Schema(description = "예정 시작 시각", example = "2026-10-21T00:00:00Z")
            Instant startTime,

            @Schema(description = "예정 종료 시각", example = "2026-10-21T02:00:00Z")
            Instant endTime,

            @Schema(description = "고객 이름. 없으면 null", example = "박서준")
            String customerName,

            @Schema(description = "조회 시각에 지연된 작업인지", example = "false")
            boolean delayed) {

        static Card from(ProgressBoardInfo.Card info) {
            return new Card(
                    info.workId(),
                    info.name(),
                    TechnicianResponse.from(info.technician()),
                    info.startTime(),
                    info.endTime(),
                    info.customerName(),
                    info.delayed());
        }
    }

    @Schema(description = "작업 수 집계")
    public record Summary(
            @Schema(description = "보드의 모든 작업 수", example = "24")
            int total,

            @Schema(description = "진행 중(작업중) 작업 수", example = "8")
            int inProgress,

            @Schema(description = "지연된 작업 수(배정됨·작업전·작업중 중)", example = "1")
            int delayed,

            @Schema(description = "완료된 작업 수", example = "15")
            int completed) {

        static Summary from(ProgressBoardInfo.Summary info) {
            return new Summary(info.total(), info.inProgress(), info.delayed(), info.completed());
        }
    }
}
