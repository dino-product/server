package com.orbit.schedule.application.port.in.query.dto;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.domain.WorkStatus;

/**
 * 기사 본인 작업 목록. 내 일정(구간과 겹치는 작업전·작업중·완료), 응답 필요(배정됨 전부), 지금 수행 가능(시작 가능 시간 안의 작업전·작업중)이 함께 쓴다.
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
     * @param customerName 고객명. 입력하지 않았으면 null
     * @param customerAddress 현장 기본주소. 입력하지 않았으면 null
     * @param customerAddressDetail 현장 상세주소. 입력하지 않았으면 null
     * @param customerLocation 현장 기본주소에서 구한 좌표. 기본주소가 없거나 구하지 못했으면 null
     * @param delayed 조회 시각에 지연된 작업인지
     */
    public record ScheduledWork(
            Long workId,
            String name,
            WorkStatus status,
            int assignmentNumber,
            Instant startTime,
            Instant endTime,
            String customerName,
            String customerAddress,
            String customerAddressDetail,
            LocationInfo customerLocation,
            boolean delayed) {}
}
