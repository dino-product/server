package com.orbit.schedule.application.port.in.query.dto;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.domain.WorkStatus;

/**
 * 기사가 보는 조직의 팀 일정. 구간과 일정이 겹치는 작업(수락대기·수락됨·작업중·완료)을 읽기 전용으로 보여 준다. 다른 기사의 작업은 시간·기사 이름·상태만 담는다.
 *
 * @param slots 기사 식별자, 시작시각 순으로 정렬한 일정 칸
 */
public record TeamScheduleInfo(List<Slot> slots) {

    public TeamScheduleInfo {
        slots = List.copyOf(slots);
    }

    /**
     * 팀 일정 칸 하나.
     *
     * @param technicianName 담당 기사의 표시 이름. 찾을 수 없으면 null
     * @param mine 요청한 기사 본인의 작업인지
     * @param workId 작업 식별자. 본인 작업이 아니면 null
     * @param workName 작업명. 본인 작업이 아니면 null
     */
    public record Slot(
            Long technicianId,
            String technicianName,
            Instant startTime,
            Instant endTime,
            WorkStatus status,
            boolean mine,
            Long workId,
            String workName) {}
}
