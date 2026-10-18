package com.orbit.schedule;

import java.time.Instant;

/**
 * 작업이 취소됐다. {@code technicianId}는 취소 당시 담당 기사(배정됨·작업전·작업중)이고, 대기함 작업이면 null이다(미리 입력한 담당기사는 담당이 아니다).
 * 작업명은 발행 시점 값이다.
 */
public record WorkCancelled(Long workId, Long organizationId, Long technicianId, String workName, Instant occurredAt) {}
