package com.orbit.schedule.application.port.in.query.dto;

import java.time.Instant;

/**
 * 기사 작업 이력·통계 조회 요청. 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별한다.
 *
 * @param technicianId 이력을 볼 기사. 관리자는 반드시 주고, 기사는 비우거나 본인을 준다
 * @param from 구간 시작(포함). 기사의 배정 일정이 이 구간과 겹치는 작업을 본다
 * @param to 구간 끝(제외). from보다 늦고 from부터 최대 31일이다
 */
public record GetWorkHistoryQuery(Long accountId, Long organizationId, Long technicianId, Instant from, Instant to) {}
