package com.orbit.schedule.application.port.in.query.dto;

/** 직원 소속이 등록한 작업 중 끝나지 않은 작업이 있는지 조회. organization이 직원을 기사로 바꾸기 전에 보낸다. */
public record UnfinishedRegisteredWorksQuery(Long organizationId, Long membershipId) {}
