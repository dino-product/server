package com.orbit.schedule.application.port.in.query.dto;

/**
 * 작업 목록 검색 요청. 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별한다.
 *
 * @param filter 조건과 정렬. null이면 조건 없이 최근 등록 먼저
 * @param page 0부터 시작하는 쪽 번호. null이면 0이고, 건너뛰는 작업 수(page × size)는 10,000건 이하다
 * @param size 한 쪽의 작업 수(1~100). null이면 20
 */
public record SearchWorksQuery(
        Long accountId, Long organizationId, WorkSearchFilter filter, Integer page, Integer size) {}
