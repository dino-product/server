package com.orbit.schedule.application.port.in.command.dto;

/** 삭제된 작업 유형을 시작 전 작업에서 비우는 요청. organization이 작업 유형을 지우는 트랜잭션 안에서 보낸다. */
public record ReleaseDeletedWorkTypeCommand(Long organizationId, Long workTypeId) {}
