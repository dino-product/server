package com.orbit.schedule.application.port.in.query.dto;

import java.time.Instant;

/**
 * 작업의 완료보고.
 *
 * @param technicianId 보고를 제출한 담당 기사
 */
public record WorkCompletionReportInfo(
        Long workId, Long technicianId, Instant completedAt, CompletionReportInfo report) {}
