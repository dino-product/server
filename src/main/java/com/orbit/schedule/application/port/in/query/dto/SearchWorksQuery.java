package com.orbit.schedule.application.port.in.query.dto;

import java.time.Instant;
import java.util.Set;

import com.orbit.schedule.domain.WorkStatus;

/**
 * 작업 목록 검색 요청. 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별한다. 비운(null) 조건은 거르지 않는다.
 *
 * @param keyword 작업명·고객 이름·연락처·주소에서 찾을 글자. 앞뒤 공백을 빼고 최대 100자이며, 비우거나 공백뿐이면 거르지 않는다
 * @param statuses 이 상태 중 하나인 작업만. null이거나 비어 있으면 모든 상태
 * @param technicianId 현재 담당 기사
 * @param startFrom 일정 시작시각 하한(포함). startTo와 함께 주거나 함께 비운다
 * @param startTo 일정 시작시각 상한(제외). startFrom보다 늦다
 * @param returnedByRejectionOnly 기사가 거절해 대기함으로 돌아온 작업만
 * @param delayedOnly 지금 지연된 작업만
 * @param sort 정렬. null이면 최근 등록 먼저
 * @param page 0부터 시작하는 쪽 번호. null이면 0
 * @param size 한 쪽의 작업 수(1~100). null이면 20
 */
public record SearchWorksQuery(
        Long accountId,
        Long organizationId,
        String keyword,
        Set<WorkStatus> statuses,
        Long technicianId,
        Instant startFrom,
        Instant startTo,
        boolean returnedByRejectionOnly,
        boolean delayedOnly,
        Sort sort,
        Integer page,
        Integer size) {

    /** 정렬 기준. 같은 값끼리는 최근 등록 먼저이고, 일정 기준 정렬에서 일정이 없는 작업은 맨 뒤다. */
    public enum Sort {
        REGISTERED_DESC,
        START_TIME_ASC,
        START_TIME_DESC
    }
}
