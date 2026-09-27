package com.orbit.schedule.application.port.in.query.dto;

/** 작업 상세 조회 요청. 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별한다. */
public record GetWorkDetailQuery(Long accountId, Long organizationId, Long workId) {}
