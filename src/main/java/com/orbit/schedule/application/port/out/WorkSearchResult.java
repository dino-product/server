package com.orbit.schedule.application.port.out;

import java.util.List;

import com.orbit.schedule.domain.Work;

/**
 * 작업 목록 검색 결과 한 쪽.
 *
 * @param works 요청한 쪽의 작업. 검색 조건의 정렬 순서다
 * @param totalCount 조건에 맞는 전체 작업 수
 */
public record WorkSearchResult(List<Work> works, long totalCount) {

    public WorkSearchResult {
        works = List.copyOf(works);
    }
}
