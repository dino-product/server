package com.orbit.schedule.application.port.in.query.dto;

/**
 * 작업내역 출력 요청. 작업 목록 검색과 같은 조건·정렬로 거른 결과 전체를 담는다(쪽 없음).
 *
 * @param filter 조건과 정렬. null이면 조건 없이 최근 등록 먼저
 */
public record ExportWorksQuery(Long accountId, Long organizationId, WorkSearchFilter filter) {}
