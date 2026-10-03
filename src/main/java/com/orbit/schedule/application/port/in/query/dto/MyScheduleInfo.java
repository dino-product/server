package com.orbit.schedule.application.port.in.query.dto;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.domain.WorkStatus;

/**
 * 기사 본인 일정. 구간과 일정이 겹치는, 지금 본인이 담당인 작업(수락대기·수락됨·작업중·완료)이다.
 *
 * @param works 시작시각, 작업 식별자 순으로 정렬한 작업
 */
public record MyScheduleInfo(List<ScheduledWork> works) {

    public MyScheduleInfo {
        works = List.copyOf(works);
    }

    /**
     * 본인 일정의 작업 하나.
     *
     * @param assignmentNumber 지금 배정의 순번. 수락·거절·시작·완료보고 요청에 그대로 쓴다
     * @param customerAddress 현장 주소. 입력하지 않았으면 null
     * @param delayed 조회 시각에 지연된 작업인지
     */
    public record ScheduledWork(
            Long workId,
            String name,
            WorkStatus status,
            int assignmentNumber,
            Instant startTime,
            Instant endTime,
            String customerAddress,
            boolean delayed) {}
}
