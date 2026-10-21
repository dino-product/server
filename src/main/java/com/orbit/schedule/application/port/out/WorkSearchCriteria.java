package com.orbit.schedule.application.port.out;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * 작업 목록 검색 조건. 서비스가 입력을 검증·정규화한 뒤 만든다. null(빈 상태 집합)인 조건은 거르지 않는다. 담당 기사·시작 구간과 예정시간 정렬은 목록의
 * 담당기사·예정시간 칸과 같은 작업에 보일 일정({@link com.orbit.schedule.domain.Work#shownSchedule}) 기준이라, 완료·배정 중 취소된 작업과 미리
 * 입력한 값이나 마지막 배정 일정이 있는 대기함 작업도 걸린다.
 *
 * @param keyword 작업명·고객 이름·연락처·기본주소·상세주소 중 하나에 대소문자 구분 없이 포함되는 글자(리터럴 부분 문자열이므로 {@code %}·{@code _}·{@code \}는
 *     이스케이프한다). 연락처는 입력한 원문 그대로 비교한다. 앞뒤 공백을 뺀, 비어 있지 않은 값이다
 * @param statuses 이 상태 중 하나인 작업. 비어 있으면 모든 상태(작업 목록의 기본값인 취소 제외는 서비스가 채운다). null이 아니다
 * @param technicianId 작업에 보일 일정의 담당 기사
 * @param workTypeId 이 작업유형의 작업
 * @param registrarId 이 소속이 등록한 작업
 * @param startFrom 작업에 보일 일정의 시작시각이 이 시각 이후(포함). startTo와 함께 있거나 함께 없다
 * @param startTo 작업에 보일 일정의 시작시각이 이 시각 이전(제외)
 * @param returnedByRejectionOnly 거절로 대기함에 돌아온 작업만({@link com.orbit.schedule.domain.Work#isReturnedByRejection})
 * @param delayedAt 이 시각에 지연된 작업만({@link com.orbit.schedule.domain.Work#isDelayedAt}: 활성·등록 상태면 보일 일정의 시작시각 +
 *     예상소요시간 &lt;= delayedAt, 완료 상태면 그 값 &lt;= 완료 시각). 저장소의 현재 시각이 아니라 이 값으로 판정해 목록과 전체 수가 같은 기준을
 *     쓴다. 거르지 않으면 null
 * @param sort 정렬. null이 아니다
 * @param page 0부터 시작하는 쪽 번호. page × size는 {@link #MAX_OFFSET} 이하다
 * @param size 한 쪽의 작업 수(1 이상 {@link #MAX_SIZE} 이하)
 */
public record WorkSearchCriteria(
        String keyword,
        Set<WorkStatus> statuses,
        TechnicianId technicianId,
        WorkTypeId workTypeId,
        MembershipId registrarId,
        Instant startFrom,
        Instant startTo,
        boolean returnedByRejectionOnly,
        Instant delayedAt,
        Sort sort,
        int page,
        int size) {

    /** 건너뛸 수 있는 가장 많은 작업 수. 깊은 offset은 저장소가 앞의 행을 모두 읽어야 하므로 막는다. */
    public static final long MAX_OFFSET = 10_000;

    /** 한 쪽에 담을 수 있는 가장 많은 작업 수. */
    public static final int MAX_SIZE = 100;

    public WorkSearchCriteria {
        if (keyword != null && (keyword.isEmpty() || !keyword.equals(keyword.strip()))) {
            throw new IllegalArgumentException("keyword must be stripped and not empty");
        }
        statuses = Set.copyOf(Objects.requireNonNull(statuses, "statuses must not be null"));
        Objects.requireNonNull(sort, "sort must not be null");
        if ((startFrom == null) != (startTo == null) || (startFrom != null && !startFrom.isBefore(startTo))) {
            throw new IllegalArgumentException("startFrom must precede startTo and both must be given together");
        }
        if (page < 0 || size < 1 || size > MAX_SIZE || (long) page * size > MAX_OFFSET) {
            throw new IllegalArgumentException(
                    "size must be 1.." + MAX_SIZE + " and page must skip at most " + MAX_OFFSET + " works");
        }
    }

    /** 같은 조건의 다른 쪽. */
    public WorkSearchCriteria withPage(int newPage, int newSize) {
        return new WorkSearchCriteria(
                keyword,
                statuses,
                technicianId,
                workTypeId,
                registrarId,
                startFrom,
                startTo,
                returnedByRejectionOnly,
                delayedAt,
                sort,
                newPage,
                newSize);
    }

    /**
     * 정렬 기준. 같은 값끼리는 작업 식별자 내림차순이고, 오래된 등록 먼저만 오름차순이다. 예정시간 정렬에서 보일 일정의 시작시각이 없는 작업은 맨
     * 뒤다.
     */
    public enum Sort {
        /** 최근 등록 먼저(작업의 등록 시각 내림차순). */
        REGISTERED_DESC,
        /** 오래된 등록 먼저(작업의 등록 시각 오름차순). */
        REGISTERED_ASC,
        /** 시작시각 이른 순(예정시간 임박순). */
        START_TIME_ASC,
        /** 최근 수정 먼저(마지막 저장 시각 내림차순, {@link WorkSearchResult.SearchedWork#lastModifiedAt}). */
        LAST_MODIFIED_DESC
    }
}
