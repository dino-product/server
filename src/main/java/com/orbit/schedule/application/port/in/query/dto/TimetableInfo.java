package com.orbit.schedule.application.port.in.query.dto;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.domain.WorkStatus;

/**
 * 타임테이블. 구간과 일정이 겹치는 기사별 작업 칸과 대기함 작업 목록이다.
 *
 * @param scheduledWorks 기사 식별자, 시작시각, 작업 식별자 순으로 정렬한 작업 칸. 수락대기·수락됨·작업중·완료 작업만 담는다
 * @param backlog 작업 식별자(등록) 순으로 정렬한 대기함 작업
 */
public record TimetableInfo(List<ScheduledWork> scheduledWorks, List<BacklogWork> backlog) {

    public TimetableInfo {
        scheduledWorks = List.copyOf(scheduledWorks);
        backlog = List.copyOf(backlog);
    }

    /**
     * 기사 일정의 작업 칸 하나.
     *
     * @param delayed 조회 시각에 지연된 작업인지
     * @param conflicting 같은 기사의 다른 활성 작업과 일정이 겹치는지(확인하고 겹침을 허용한 배정). 겹치는 짝이 구간 밖에 있어도 표시한다
     */
    public record ScheduledWork(
            Long workId,
            String name,
            WorkStatus status,
            Long technicianId,
            Instant startTime,
            Instant endTime,
            boolean delayed,
            boolean conflicting) {}

    /**
     * 대기함 작업 하나.
     *
     * @param workTypeId 작업 유형. 없으면 null
     * @param returnedByRejection 기사가 거절해 대기함으로 돌아온 작업인지(최신 배정이 거절됨)
     */
    public record BacklogWork(Long workId, String name, Long workTypeId, boolean returnedByRejection) {}
}
