package com.orbit.schedule.application.port.in.query.dto;

/** 완료보고 조회 요청. 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별한다. */
public record GetCompletionReportQuery(Long accountId, Long organizationId, Long workId) {}
