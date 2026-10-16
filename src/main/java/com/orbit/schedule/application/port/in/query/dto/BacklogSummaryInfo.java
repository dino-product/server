package com.orbit.schedule.application.port.in.query.dto;

import java.util.List;

/**
 * 작업 목록 위에 고정하는 대기함 요약 한 쪽.
 *
 * @param backlogCount 대기함에 남은 작업 수(거절로 돌아온 작업 포함)
 * @param rejectedCount 그 가운데 기사가 거절해 돌아온 작업 수
 * @param works 대기함 작업 카드. 등록 시각·작업 식별자 순(오래된 순)이고, 쪽이 끝을 넘으면 비어 있다
 * @param page 적용한 쪽 번호(0부터)
 * @param size 적용한 한 쪽의 작업 수
 */
public record BacklogSummaryInfo(
        long backlogCount, long rejectedCount, List<BacklogWorkInfo> works, int page, int size) {

    public BacklogSummaryInfo {
        works = List.copyOf(works);
    }
}
