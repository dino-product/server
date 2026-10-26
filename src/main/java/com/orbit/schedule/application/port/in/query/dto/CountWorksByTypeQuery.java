package com.orbit.schedule.application.port.in.query.dto;

/** 작업 유형 삭제 전 영향 건수 조회. organization이 작업 유형을 지우기 전에 보낸다. */
public record CountWorksByTypeQuery(Long organizationId, Long workTypeId) {}
