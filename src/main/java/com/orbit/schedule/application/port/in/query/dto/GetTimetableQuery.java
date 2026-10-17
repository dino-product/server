package com.orbit.schedule.application.port.in.query.dto;

import java.time.Instant;
import java.util.Set;

/**
 * 타임테이블 조회 요청. 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별한다. 화면의 날짜를 조직의 현지 시간대로 바꾼 UTC 구간을 받는다.
 * 유형 조건은 기사 행과 작업 칸에만 걸고 대기함에는 걸지 않는다.
 *
 * @param from 구간 시작(포함)
 * @param to 구간 끝(제외). from보다 늦고 from부터 최대 31일이다
 * @param technicianTypeIds 이 기사유형 중 하나인 기사의 행과 작업만. null이거나 비어 있으면 거르지 않는다
 * @param workTypeIds 이 작업유형 중 하나인 작업 칸만. null이거나 비어 있으면 거르지 않는다
 */
public record GetTimetableQuery(
        Long accountId,
        Long organizationId,
        Instant from,
        Instant to,
        Set<Long> technicianTypeIds,
        Set<Long> workTypeIds) {}
