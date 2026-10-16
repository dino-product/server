package com.orbit.schedule.application.port.in.command.dto;

/**
 * 작업 등록 결과. 배정 없이 등록했거나 배정까지 반영했으면 새 작업 식별자와 반영된 일정 결과를 담는다. 함께 배정하려던 일정이 겹치는데 확인하지 않았으면 작업을
 * 만들지 않으므로 식별자가 없고({@code null}) 일정 결과는 반영 보류와 겹친 작업이다.
 */
public record CreatedWorkInfo(Long workId, ScheduleChangeInfo scheduleChange) {}
