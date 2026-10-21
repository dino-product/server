package com.orbit.schedule.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 사무실이 담당 기사에게 완료보고를 요청한 기록. 요청 시각과 요청한 관리자(조직 소속)를 담는다. 요청 시각은 저장소 정밀도인 마이크로초로 잘라 둔다.
 */
public record CompletionReportRequest(Instant requestedAt, MembershipId requestedBy) {

    public CompletionReportRequest {
        if (requestedAt == null) {
            throw new IllegalArgumentException("requestedAt must not be null");
        }
        if (requestedBy == null) {
            throw new IllegalArgumentException("requestedBy must not be null");
        }
        requestedAt = requestedAt.truncatedTo(ChronoUnit.MICROS);
    }
}
