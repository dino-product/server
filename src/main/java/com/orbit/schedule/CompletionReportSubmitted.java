package com.orbit.schedule;

import java.time.Instant;

/** 담당 기사가 완료보고를 제출해 작업이 완료됐다. {@code registrarMembershipId}는 작업을 등록한 소속이다. 작업명은 발행 시점 값이다. */
public record CompletionReportSubmitted(
        Long workId,
        Long organizationId,
        Long registrarMembershipId,
        Long technicianId,
        String workName,
        Instant occurredAt) {}
