package com.orbit.schedule.adapter.in.web.board;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.adapter.in.web.TechnicianResponse;
import com.orbit.schedule.application.port.in.query.dto.ProgressBoardInfo;
import com.orbit.schedule.domain.WorkStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "진행 보드")
public record ProgressBoardResponse(
        @Schema(description = "등록·배정됨·작업전·작업중·완료 순서의 열. 작업이 없는 상태도 빈 열로 담는다")
        List<Column> columns,

        @Schema(description = "작업 수 집계") Summary summary) {

    static ProgressBoardResponse from(ProgressBoardInfo info) {
        return new ProgressBoardResponse(
                info.columns().stream().map(Column::from).toList(), Summary.from(info.summary()));
    }

    @Schema(description = "한 상태의 열")
    public record Column(
            @Schema(
                    description = "상태. REGISTERED(등록), PENDING_ACCEPTANCE(배정됨), ACCEPTED(작업전), IN_PROGRESS(작업중), "
                            + "COMPLETED(완료)",
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

            @Schema(description = "담당 기사. 등록 작업은 미리 입력한 담당기사나 마지막 배정의 기사이고, 없으면 null")
            TechnicianResponse technician,

            @Schema(description = "예정 시작 시각. 등록 작업은 미리 입력한 값이나 마지막 배정의 시각", example = "2026-10-21T00:00:00Z")
            Instant startTime,

            @Schema(description = "예정 종료 시각. 예상 소요시간을 모르는 등록 작업이면 null", example = "2026-10-21T02:00:00Z")
            Instant endTime,

            @Schema(description = "고객 이름. 없으면 null", example = "박서준")
            String customerName,

            @Schema(
                    description = "조회 시각에 지연된 작업인지. 예정 종료 시각이 지났는데 끝나지 않은 작업(시간이 있는 등록 작업 포함)과 예정 종료 시각이나 그 뒤에 완료한 작업",
                    example = "false")
            boolean delayed,

            @Schema(description = "기사가 거절해 대기함으로 돌아온 작업인지(거절 표시)", example = "false")
            boolean returnedByRejection) {

        static Card from(ProgressBoardInfo.Card info) {
            return new Card(
                    info.workId(),
                    info.name(),
                    TechnicianResponse.from(info.technician()),
                    info.startTime(),
                    info.endTime(),
                    info.customerName(),
                    info.delayed(),
                    info.returnedByRejection());
        }
    }

    @Schema(description = "작업 수 집계")
    public record Summary(
            @Schema(description = "보드의 모든 작업 수", example = "24")
            int total,

            @Schema(description = "진행 중(작업중) 작업 수", example = "8")
            int inProgress,

            @Schema(description = "지금 지연 중인 작업 수(등록·배정됨·작업전·작업중 중. 늦게 완료한 작업은 세지 않음)", example = "1")
            int delayed,

            @Schema(description = "완료된 작업 수", example = "15")
            int completed) {

        static Summary from(ProgressBoardInfo.Summary info) {
            return new Summary(info.total(), info.inProgress(), info.delayed(), info.completed());
        }
    }
}
