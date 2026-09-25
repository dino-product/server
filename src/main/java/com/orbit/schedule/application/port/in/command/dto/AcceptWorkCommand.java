package com.orbit.schedule.application.port.in.command.dto;

/**
 * 배정 수락 요청. 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별한다.
 *
 * @param assignmentNumber 기사가 화면에서 본 배정의 순번(배정 이력의 1부터 시작하는 위치). 그 뒤로 배정이 바뀌었으면 수락하지 않는다
 */
public record AcceptWorkCommand(Long accountId, Long organizationId, Long workId, Integer assignmentNumber) {}
