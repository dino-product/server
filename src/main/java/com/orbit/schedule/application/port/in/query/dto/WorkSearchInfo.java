package com.orbit.schedule.application.port.in.query.dto;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.domain.WorkStatus;

/**
 * 작업 목록 검색 결과 한 쪽.
 *
 * @param works 요청한 정렬 순서의 작업. 쪽이 끝을 넘으면 비어 있다
 * @param totalCount 조건에 맞는 전체 작업 수. 쪽과 관계없다
 * @param page 적용한 쪽 번호(0부터)
 * @param size 적용한 한 쪽의 작업 수
 */
public record WorkSearchInfo(List<WorkSummary> works, long totalCount, int page, int size) {

    public WorkSearchInfo {
        works = List.copyOf(works);
    }

    /**
     * 목록의 작업 한 줄. 일정이 없는 작업(대기함, 배정 전에 취소된 작업)은 기사·시각이 null이다.
     *
     * @param customerName 고객 이름. 입력하지 않았으면 null
     * @param delayed 조회 시각에 지연된 작업인지
     * @param returnedByRejection 기사가 거절해 대기함으로 돌아온 작업인지
     */
    public record WorkSummary(
            Long workId,
            String name,
            WorkStatus status,
            Long technicianId,
            Instant startTime,
            Instant endTime,
            String customerName,
            boolean delayed,
            boolean returnedByRejection) {}
}
