package com.orbit.schedule.application.port.out;

import java.util.List;

import com.orbit.schedule.domain.Work;

/**
 * 작업 목록 검색 결과 한 쪽.
 *
 * @param works 요청한 쪽의 작업. 검색 조건의 정렬 순서이고, 쪽이 끝을 넘으면 비어 있다
 * @param totalCount 조건에 맞는 전체 작업 수. 쪽과 관계없다
 */
public record WorkSearchResult(List<Work> works, long totalCount) {

    public WorkSearchResult {
        works = List.copyOf(works);
    }
}
