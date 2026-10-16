package com.orbit.schedule.application.port.in.query.dto;

/**
 * 계정의 활성 기사 계약 전체에 걸쳐 탈퇴를 막는 작업 수. 둘 다 0이면 schedule은 탈퇴를 막지 않는다.
 *
 * @param inProgressCount 작업중 작업 수
 * @param pendingAcceptanceCount 기사가 아직 응답하지 않은 배정됨 작업 수
 */
public record WithdrawalBlockingWorksInfo(int inProgressCount, int pendingAcceptanceCount) {}
