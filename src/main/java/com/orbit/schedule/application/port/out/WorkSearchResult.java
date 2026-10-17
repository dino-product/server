package com.orbit.schedule.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.orbit.schedule.domain.Work;

/**
 * 작업 목록 검색 결과 한 쪽.
 *
 * @param works 요청한 쪽의 작업. 검색 조건의 정렬 순서이고, 쪽이 끝을 넘으면 비어 있다
 * @param totalCount 조건에 맞는 전체 작업 수. 쪽과 관계없다
 */
public record WorkSearchResult(List<SearchedWork> works, long totalCount) {

    public WorkSearchResult {
        works = List.copyOf(works);
    }

    /**
     * 검색된 작업 하나.
     *
     * @param work 작업
     * @param lastModifiedAt 작업을 마지막으로 저장한 시각(목록의 최근 수정일). 업무 기록 시각이 아니라 저장소가 남긴 값이다
     */
    public record SearchedWork(Work work, Instant lastModifiedAt) {

        public SearchedWork {
            Objects.requireNonNull(work, "work must not be null");
            Objects.requireNonNull(lastModifiedAt, "lastModifiedAt must not be null");
        }
    }
}
