package com.orbit.schedule.adapter.in.web.myschedule;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.adapter.in.web.LocationResponse;
import com.orbit.schedule.application.port.in.query.dto.MyScheduleInfo;
import com.orbit.schedule.domain.WorkStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기사 본인 작업 목록")
public record MyScheduleResponse(
        @Schema(description = "작업. 시작 시각·작업 식별자 순") List<ScheduledWork> works) {

    static MyScheduleResponse from(MyScheduleInfo info) {
        return new MyScheduleResponse(
                info.works().stream().map(ScheduledWork::from).toList());
    }

    @Schema(description = "지금 본인이 담당인 작업")
    public record ScheduledWork(
            @Schema(description = "작업 식별자", example = "10") Long workId,
            @Schema(description = "작업명", example = "보일러 점검") String name,

            @Schema(
                    description = "작업 상태. PENDING_ACCEPT(배정됨), ACCEPTED(작업전), IN_PROGRESS(작업중), COMPLETED(완료)",
                    example = "ACCEPTED")
            WorkStatus status,

            @Schema(description = "지금 배정의 순번. 수락·거절·시작·완료보고 요청의 assignmentNumber로 그대로 보낸다", example = "1")
            int assignmentNumber,

            @Schema(description = "시작 시각", example = "2026-10-21T00:00:00Z")
            Instant startTime,

            @Schema(description = "종료 시각", example = "2026-10-21T02:00:00Z")
            Instant endTime,

            @Schema(description = "고객명. 없으면 null", example = "홍길동")
            String customerName,

            @Schema(description = "현장 기본주소. 없으면 null", example = "서울시 중구 세종대로 110")
            String customerAddress,

            @Schema(description = "현장 상세주소. 없으면 null", example = "3층")
            String customerAddressDetail,

            @Schema(description = "현장 좌표. 주소가 없거나 좌표를 찾지 못했으면 null")
            LocationResponse customerLocation,

            @Schema(description = "조회 시각에 지연된 작업인지", example = "false")
            boolean delayed) {

        static ScheduledWork from(MyScheduleInfo.ScheduledWork info) {
            return new ScheduledWork(
                    info.workId(),
                    info.name(),
                    info.status(),
                    info.assignmentNumber(),
                    info.startTime(),
                    info.endTime(),
                    info.customerName(),
                    info.customerAddress(),
                    info.customerAddressDetail(),
                    LocationResponse.from(info.customerLocation()),
                    info.delayed());
        }
    }
}
