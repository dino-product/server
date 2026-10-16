package com.orbit.schedule.application.port.in.query.dto;

/** 탈퇴하려는 계정의 기사 작업 중 탈퇴를 막는 작업 조회. 계정은 인증을 마친 탈퇴 요청자다. */
public record GetWithdrawalBlockingWorksQuery(Long accountId) {}
