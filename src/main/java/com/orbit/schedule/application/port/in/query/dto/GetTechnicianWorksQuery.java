package com.orbit.schedule.application.port.in.query.dto;

/** 기사 본인 작업 목록 조회 요청(응답 필요·지금 수행 가능). 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별한다. */
public record GetTechnicianWorksQuery(Long accountId, Long organizationId) {}
