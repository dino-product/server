package com.orbit.schedule.application.port.in.query.dto;

/** 기사 작업 이력 목록의 상태 조건. 작업 상태가 아니라 그 기사 기준의 결과다. */
public enum WorkHistoryStatus {
    /** 그 기사가 지금 담당인 채 완료한 작업. */
    COMPLETED,
    /** 그 기사가 시작시각이 구간 안인 배정을 한 번이라도 거절한 작업(다시 배정받아 완료했어도 담는다). 통계의 거절 수와 같은 기준이다. */
    REJECTED
}
