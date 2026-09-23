package com.orbit.schedule.application.port.in.command.dto;

import java.time.Duration;
import java.time.Instant;

/**
 * 같은 담당기사의 일정 변경 요청. 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별한다. 담당기사는 그대로 두고 시작시각·예상소요시간을
 * 바꾼다.
 *
 * @param conflictConfirmed 일정 겹침 경고를 확인하고 그대로 반영할지. false이고 겹치면 반영하지 않고 겹친 작업을 돌려준다. true면 확인 이후 새로 생긴 겹침도
 *     다시 묻지 않고 반영한다
 */
public record RescheduleWorkCommand(
        Long accountId,
        Long organizationId,
        Long workId,
        Instant startTime,
        Duration expectedDuration,
        boolean conflictConfirmed) {}
