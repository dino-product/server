package com.orbit.schedule.application.port.in.command.dto;

import java.time.Duration;
import java.time.Instant;

/**
 * 작업의 담당기사·일정을 요청한 값으로 맞추는 요청. 현재 배정과 비교해 배정·재배정·일정 변경 중 하나로 처리한다.
 *
 * @param expectedVersion 요청자가 본 작업 버전. 지금 버전과 다르면 반영하지 않는다
 */
public record ChangeWorkAssignmentCommand(
        Long accountId,
        Long organizationId,
        Long workId,
        Long technicianId,
        Instant startTime,
        Duration expectedDuration,
        boolean conflictConfirmed,
        Long expectedVersion) {}
