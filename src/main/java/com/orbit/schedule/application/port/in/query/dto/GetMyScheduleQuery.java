package com.orbit.schedule.application.port.in.query.dto;

import java.time.Instant;

/**
 * 기사 본인 일정 조회 요청. 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별한다. 화면의 날짜를 조직의 현지 시간대로 바꾼 UTC 구간을 받는다.
 *
 * @param from 구간 시작(포함)
 * @param to 구간 끝(제외). from보다 늦고 from부터 최대 31일이다
 */
public record GetMyScheduleQuery(Long accountId, Long organizationId, Instant from, Instant to) {}
