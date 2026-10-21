package com.orbit.schedule;

import java.time.Instant;

/**
 * 배정됐거나 수락된 작업의 시작시각·예상소요시간이 바뀌었다(담당 기사는 그대로). {@code accepted}는 기사가 수락한 상태였는지이며, 수락한 작업은 바뀐 뒤에도
 * 수락 상태를 유지한다. 작업명은 발행 시점 값이다.
 */
public record WorkRescheduled(
        Long workId,
        Long organizationId,
        Long technicianId,
        boolean accepted,
        String workName,
        Instant scheduledStartAt,
        Instant occurredAt) {}
