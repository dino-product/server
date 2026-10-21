package com.orbit.schedule;

import java.time.Instant;

/** 사무실이 예정 종료가 지난 작업중 작업의 담당 기사에게 완료보고를 요청했다. 작업명은 발행 시점 값이다. */
public record CompletionReportRequested(
        Long workId, Long organizationId, Long technicianId, String workName, Instant occurredAt) {}
