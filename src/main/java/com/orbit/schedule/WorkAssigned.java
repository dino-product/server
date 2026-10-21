package com.orbit.schedule;

import java.time.Instant;

/**
 * 대기함 작업이 담당 기사에게 배정됐다(배정 변경으로 배정하거나 일정을 함께 준 등록). 기사가 응답해야 하는 배정이다. 다른 기사로 바꾼 배정은
 * {@link WorkReassigned}다. 작업명은 받는 쪽 문구에 쓰려고 발행 시점 값을 담는다(리스너가 커밋 뒤에 돌아 나중에 읽으면 바뀐 값을 본다).
 */
public record WorkAssigned(
        Long workId,
        Long organizationId,
        Long technicianId,
        String workName,
        Instant scheduledStartAt,
        Instant occurredAt) {}
