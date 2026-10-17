package com.orbit.schedule.adapter.in.web.search;

import java.util.List;

import com.orbit.schedule.adapter.in.web.BacklogWorkResponse;
import com.orbit.schedule.application.port.in.query.dto.BacklogSummaryInfo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "대기함 요약 한 쪽(작업 목록 상단의 대기함 잔여·거절 작업)")
public record BacklogSummaryResponse(
        @Schema(description = "대기함에 남은 작업 수(거절로 돌아온 작업 포함). 쪽 나누기의 전체 수", example = "15")
        long totalCount,

        @Schema(description = "그 가운데 거절로 돌아온 작업을 뺀 수(화면의 '등록 N건')", example = "12")
        long registeredCount,

        @Schema(description = "그 가운데 기사가 거절해 돌아온 작업 수(화면의 '거절 N건')", example = "3")
        long rejectedCount,

        @Schema(description = "대기함 작업 카드. 등록 시각·작업 식별자 순(오래된 순)")
        List<BacklogWorkResponse> works,

        @Schema(description = "적용한 쪽 번호(0부터)", example = "0")
        int page,

        @Schema(description = "적용한 한 쪽의 작업 수", example = "5")
        int size) {

    static BacklogSummaryResponse from(BacklogSummaryInfo info) {
        return new BacklogSummaryResponse(
                info.totalCount(),
                info.registeredCount(),
                info.rejectedCount(),
                info.works().stream().map(BacklogWorkResponse::from).toList(),
                info.page(),
                info.size());
    }
}
