package com.orbit.schedule.application.port.in.query.dto;

import java.time.Instant;

/**
 * 작업의 현재 완료보고. 관리자 강제 변경으로 치운 이전 보고는 담지 않는다(관리자에게는 작업 상세의 강제 변경 기록으로 보인다).
 *
 * @param technicianId 보고를 제출한 담당 기사
 */
public record WorkCompletionReportInfo(
        Long workId, Long technicianId, Instant completedAt, CompletionReportInfo report) {}
