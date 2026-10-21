package com.orbit.schedule;

import java.time.Instant;

/**
 * 배정됐거나 수락된 작업의 담당 기사가 다른 기사로 바뀌었다. 새 기사는 다시 수락해야 한다. {@code previousAccepted}는 이전 기사가 수락한 상태였는지다.
 * 작업명은 발행 시점 값이다.
 */
public record WorkReassigned(
        Long workId,
        Long organizationId,
        Long previousTechnicianId,
        Long technicianId,
        boolean previousAccepted,
        String workName,
        Instant scheduledStartAt,
        Instant occurredAt) {}
