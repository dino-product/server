package com.orbit.schedule.application.port.out;

import java.time.Instant;
import java.util.Set;

import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 작업 목록 검색 조건. 서비스가 입력을 검증·정규화한 뒤 만든다. 비어 있는(null·빈 집합) 조건은 거르지 않는다.
 *
 * @param keyword 작업명·고객 이름·연락처·주소 중 하나에 대소문자 구분 없이 포함되는 글자. 앞뒤 공백을 뺀 값이다
 * @param statuses 이 상태 중 하나인 작업. 비어 있으면 모든 상태
 * @param technicianId 현재 일정의 담당 기사
 * @param startFrom 현재 일정의 시작시각이 이 시각 이후(포함). startTo와 함께 있거나 함께 없다
 * @param startTo 현재 일정의 시작시각이 이 시각 이전(제외)
 * @param returnedByRejectionOnly 거절로 대기함에 돌아온 작업만({@link com.orbit.schedule.domain.Work#isReturnedByRejection})
 * @param delayedAt 이 시각에 지연된 작업만({@link com.orbit.schedule.domain.Work#isDelayedAt}). 거르지 않으면 null
 * @param page 0부터 시작하는 쪽 번호
 * @param size 한 쪽의 작업 수
 */
public record WorkSearchCriteria(
        String keyword,
        Set<WorkStatus> statuses,
        TechnicianId technicianId,
        Instant startFrom,
        Instant startTo,
        boolean returnedByRejectionOnly,
        Instant delayedAt,
        Sort sort,
        int page,
        int size) {

    public WorkSearchCriteria {
        statuses = Set.copyOf(statuses);
    }

    /** 정렬 기준. 같은 값끼리는 작업 식별자 내림차순(최근 등록 먼저)이다. 일정 기준 정렬에서 일정이 없는 작업은 맨 뒤다. */
    public enum Sort {
        /** 최근 등록 먼저. */
        REGISTERED_DESC,
        /** 시작시각 이른 순. */
        START_TIME_ASC,
        /** 시작시각 늦은 순. */
        START_TIME_DESC
    }
}
