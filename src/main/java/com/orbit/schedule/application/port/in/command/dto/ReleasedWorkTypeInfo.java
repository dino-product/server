package com.orbit.schedule.application.port.in.command.dto;

/**
 * 유형을 비운 작업.
 *
 * @param workId 작업 식별자
 * @param technicianId 수락대기·수락됨 작업의 담당 기사 계약 식별자. 등록(대기함) 작업이면 null
 */
public record ReleasedWorkTypeInfo(Long workId, Long technicianId) {}
