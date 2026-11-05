package com.orbit.schedule.application.port.in.query.dto;

/** 기사 작업 이력 목록의 상태 조건. 작업 상태가 아니라 그 기사 기준의 결과다. */
public enum WorkHistoryStatus {
    /** 그 기사가 지금 담당인 채 완료한 작업. */
    COMPLETED,
    /** 그 기사가 거절한 배정(목록 행의 배정 결과가 거절). */
    REJECTED
}
