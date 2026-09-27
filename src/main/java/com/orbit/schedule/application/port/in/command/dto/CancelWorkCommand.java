package com.orbit.schedule.application.port.in.command.dto;

/**
 * 작업 취소 요청. 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별한다.
 *
 * @param reason 취소 사유. 필수이며 최대 255자다
 */
public record CancelWorkCommand(Long accountId, Long organizationId, Long workId, String reason) {}
