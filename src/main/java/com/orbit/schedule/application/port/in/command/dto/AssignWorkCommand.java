package com.orbit.schedule.application.port.in.command.dto;

import java.time.Duration;
import java.time.Instant;

/**
 * 대기함 작업 배정 요청. 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별한다. 담당기사·시작시각·예상소요시간을 한 번에 지정한다.
 *
 * @param technicianMembershipId 담당기사의 조직 소속 식별자
 * @param conflictConfirmed 일정 겹침 경고를 확인하고 그대로 배정할지. false이고 겹치면 반영하지 않고 겹친 작업을 돌려준다. true면 확인 이후 새로 생긴 겹침도
 *     다시 묻지 않고 반영한다
 */
public record AssignWorkCommand(
        Long accountId,
        Long organizationId,
        Long workId,
        Long technicianMembershipId,
        Instant startTime,
        Duration expectedDuration,
        boolean conflictConfirmed) {}
