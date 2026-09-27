package com.orbit.schedule.application.port.out;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 작업 목록 검색 조건. 서비스가 입력을 검증·정규화한 뒤 만든다. null(빈 상태 집합)인 조건은 거르지 않는다. 담당 기사·시작 구간은 작업에 남은 일정 기준이라 완료·배정 중
 * 취소된 작업도 걸리고, 일정이 없는 대기함 작업은 걸리지 않는다.
 *
 * @param keyword 작업명·고객 이름·연락처·주소 중 하나에 대소문자 구분 없이 포함되는 글자(리터럴 부분 문자열이므로 {@code %}·{@code _}·{@code \}는
 *     이스케이프한다). 연락처는 입력한 원문 그대로 비교한다. 앞뒤 공백을 뺀, 비어 있지 않은 값이다
 * @param statuses 이 상태 중 하나인 작업. 비어 있으면 모든 상태. null이 아니다
 * @param technicianId 작업에 남은 일정의 담당 기사
 * @param startFrom 작업에 남은 일정의 시작시각이 이 시각 이후(포함). startTo와 함께 있거나 함께 없다
 * @param startTo 작업에 남은 일정의 시작시각이 이 시각 이전(제외)
 * @param returnedByRejectionOnly 거절로 대기함에 돌아온 작업만({@link com.orbit.schedule.domain.Work#isReturnedByRejection})
 * @param delayedAt 이 시각에 지연된 작업만({@link com.orbit.schedule.domain.Work#isDelayedAt}: 활성 상태이고 시작시각 + 예상소요시간 &lt;=
 *     delayedAt). 저장소의 현재 시각이 아니라 이 값으로 판정해 목록과 전체 수가 같은 기준을 쓴다. 거르지 않으면 null
 * @param sort 정렬. null이 아니다
 * @param page 0부터 시작하는 쪽 번호. page × size는 {@link #MAX_OFFSET} 이하다
 * @param size 한 쪽의 작업 수(1 이상)
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

    /** 건너뛸 수 있는 가장 많은 작업 수. 깊은 offset은 저장소가 앞의 행을 모두 읽어야 하므로 막는다. */
    public static final long MAX_OFFSET = 10_000;

    public WorkSearchCriteria {
        if (keyword != null && (keyword.isEmpty() || !keyword.equals(keyword.strip()))) {
            throw new IllegalArgumentException("keyword must be stripped and not empty");
        }
        statuses = Set.copyOf(Objects.requireNonNull(statuses, "statuses must not be null"));
        Objects.requireNonNull(sort, "sort must not be null");
        if ((startFrom == null) != (startTo == null) || (startFrom != null && !startFrom.isBefore(startTo))) {
            throw new IllegalArgumentException("startFrom must precede startTo and both must be given together");
        }
        if (page < 0 || size < 1 || (long) page * size > MAX_OFFSET) {
            throw new IllegalArgumentException("page and size must select at most " + MAX_OFFSET + " skipped works");
        }
    }

    /**
     * 정렬 기준. 같은 값끼리는 작업 식별자 내림차순이다. 일정 기준 정렬에서 일정이 없는 작업은 오름·내림차순 모두 맨 뒤다(PostgreSQL 내림차순의 기본값은 NULLS
     * FIRST이므로 NULLS LAST를 명시한다).
     */
    public enum Sort {
        /** 최근 등록 먼저. 도메인에 등록 시각이 없으므로 작업 식별자 내림차순이다(감사 컬럼 생성 시각으로 정렬하지 않는다). */
        REGISTERED_DESC,
        /** 시작시각 이른 순. */
        START_TIME_ASC,
        /** 시작시각 늦은 순. */
        START_TIME_DESC
    }
}
