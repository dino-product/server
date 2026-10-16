package com.orbit.schedule.application.port.in.query.dto;

import java.time.Instant;
import java.util.Set;

import com.orbit.schedule.domain.WorkStatus;

/**
 * 작업 목록의 조건과 정렬. 작업 목록 검색과 작업내역 출력이 함께 쓴다. 비운(null) 조건은 거르지 않는다.
 *
 * @param keyword 작업명·고객 이름·연락처·주소 중 하나에 대소문자 구분 없이 포함되는 글자. 앞뒤 공백을 빼고 최대 100자이며, 비우거나 공백뿐이면 거르지 않는다
 * @param statuses 이 상태 중 하나인 작업만. null이거나 비어 있으면 모든 상태. null 원소는 받지 않는다
 * @param technicianId 작업에 남은 일정의 담당 기사(완료·배정 중 취소된 작업 포함)
 * @param workTypeId 이 작업유형의 작업만
 * @param registrarId 이 소속(직원·총관리자)이 등록한 작업만
 * @param startFrom 작업에 남은 일정의 시작시각 하한(포함). startTo와 함께 주거나 함께 비우며, 1970년부터 9999년까지다
 * @param startTo 일정 시작시각 상한(제외). startFrom보다 늦다
 * @param returnedByRejectionOnly 기사가 거절해 대기함으로 돌아온 작업만
 * @param delayedOnly 지금 지연된 작업만
 * @param sort 정렬. null이면 최근 등록 먼저
 */
public record WorkSearchFilter(
        String keyword,
        Set<WorkStatus> statuses,
        Long technicianId,
        Long workTypeId,
        Long registrarId,
        Instant startFrom,
        Instant startTo,
        boolean returnedByRejectionOnly,
        boolean delayedOnly,
        Sort sort) {

    /** 조건 없이 최근 등록 먼저. */
    public static WorkSearchFilter none() {
        return new WorkSearchFilter(null, null, null, null, null, null, null, false, false, null);
    }

    /** 정렬 기준. 같은 값끼리는 작업 식별자 내림차순(오래된 등록 먼저만 오름차순)이고, 예정시간 정렬에서 일정이 없는 작업은 맨 뒤다. */
    public enum Sort {
        /** 등록일 최신순. */
        REGISTERED_DESC,
        /** 등록일 오래된순. */
        REGISTERED_ASC,
        /** 예정시간 임박순(시작시각 이른 순). */
        START_TIME_ASC,
        /** 최근 수정일순. */
        LAST_MODIFIED_DESC
    }
}
