package com.orbit.schedule.adapter.in.web.timetable;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.adapter.in.web.BacklogWorkResponse;
import com.orbit.schedule.adapter.in.web.LabelResponse;
import com.orbit.schedule.adapter.in.web.StaffResponse;
import com.orbit.schedule.adapter.in.web.TechnicianResponse;
import com.orbit.schedule.application.port.in.query.dto.TimetableInfo;
import com.orbit.schedule.domain.WorkStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "타임테이블")
public record TimetableResponse(
        @Schema(description = "기사 행. 활성 기사를 이름(없으면 맨 뒤)·기사 계약 식별자 순으로 담는다")
        List<TechnicianResponse> technicians,

        @Schema(description = "작업 칸. 기사 식별자·시작 시각·작업 식별자 순. 기사 행에 없는 기사(비활성화된 기사 등)의 칸도 담는다")
        List<ScheduledWork> scheduledWorks,

        @Schema(description = "대기함 작업. 등록 시각·작업 식별자 순(오래된 순)")
        List<BacklogWorkResponse> backlog) {

    static TimetableResponse from(TimetableInfo info) {
        return new TimetableResponse(
                info.technicians().stream().map(TechnicianResponse::from).toList(),
                info.scheduledWorks().stream().map(ScheduledWork::from).toList(),
                info.backlog().stream().map(BacklogWorkResponse::from).toList());
    }

    @Schema(description = "기사 일정의 작업 칸")
    public record ScheduledWork(
            @Schema(description = "작업 식별자", example = "10") Long workId,
            @Schema(description = "작업명", example = "인덕션 설치") String name,

            @Schema(
                    description = "작업 상태. PENDING_ACCEPT(배정됨), ACCEPTED(작업전), IN_PROGRESS(작업중), COMPLETED(완료)",
                    example = "ACCEPTED")
            WorkStatus status,

            @Schema(description = "담당 기사") TechnicianResponse technician,

            @Schema(description = "시작 시각", example = "2026-10-21T00:00:00Z")
            Instant startTime,

            @Schema(description = "종료 시각", example = "2026-10-21T02:00:00Z")
            Instant endTime,

            @Schema(description = "조회 시각에 지연된 작업인지", example = "false")
            boolean delayed,

            @Schema(description = "같은 기사의 다른 활성 작업과 일정이 겹치는지", example = "false")
            boolean conflicting,

            @Schema(description = "작업유형. 없으면 null") LabelResponse workType,
            @Schema(description = "등록자") StaffResponse registrar,

            @Schema(description = "작업 버전. 배정 변경·해제 요청의 expectedVersion으로 그대로 보낸다", example = "3")
            long version) {

        static ScheduledWork from(TimetableInfo.ScheduledWork info) {
            return new ScheduledWork(
                    info.workId(),
                    info.name(),
                    info.status(),
                    TechnicianResponse.from(info.technician()),
                    info.startTime(),
                    info.endTime(),
                    info.delayed(),
                    info.conflicting(),
                    LabelResponse.from(info.workType()),
                    StaffResponse.from(info.registrar()),
                    info.version());
        }
    }
}
