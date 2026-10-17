package com.orbit.schedule.application.port.in.query.dto;

/**
 * 작업 목록의 대기함 요약 조회 요청. 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별한다.
 *
 * @param page 0부터 시작하는 쪽 번호. null이면 0이고, 건너뛰는 작업 수(page × size)는 10,000건 이하다
 * @param size 한 쪽의 작업 수(1~100). null이면 5
 */
public record GetBacklogSummaryQuery(Long accountId, Long organizationId, Integer page, Integer size) {}
