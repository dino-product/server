package com.orbit.schedule.application.port.in.command.dto;

/** 담당 기사에게 완료보고를 요청한다. 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별한다. */
public record RequestCompletionReportCommand(Long accountId, Long organizationId, Long workId) {}
