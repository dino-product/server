package com.orbit.schedule;

import java.time.Instant;

/** 담당 기사가 배정을 수락했다. {@code registrarMembershipId}는 작업을 등록한 직원·총관리자의 소속이다. 작업명은 발행 시점 값이다. */
public record WorkAccepted(
        Long workId,
        Long organizationId,
        Long registrarMembershipId,
        Long technicianId,
        String workName,
        Instant occurredAt) {}
