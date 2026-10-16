package com.orbit.schedule.application.port.in.command.dto;

/** 탈퇴한 계정의 기사 예정 작업을 대기함으로 돌리는 요청. 계정은 이미 탈퇴가 커밋된 계정이다. */
public record ReleaseWithdrawnTechnicianWorksCommand(Long accountId) {}
